package reasoning.support

import reasoning.belief.application.SettableClock
import reasoning.belief.domain.Moment
import reasoning.process.appraiser.{Appraiser, Document, GoldenSet, Instructions}
import ujson.{Arr, Obj}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt

/**
 * What the live suite asserts of a run of the appraiser against the real providers, shared by the
 * plain suite and the living feature. A real model's words are not asserted; the direction of each
 * move, the critique passing, and every record held with its act are.
 */
trait LiveAppraisal:
  self: munit.Assertions =>

  protected def http: Http
  protected def clock: SettableClock

  protected lazy val goldenSet: GoldenSet = GoldenSet(ServiceFixture.settings.goldenSet)

  protected val Blueprint: String = Appraiser.Name
  protected val LiveHolder        = "appraiser-a"

  /** The last day of a tranche's month, after everything in it is published. */
  protected def trancheDay(tranche: String): Moment =
    Moment.parse(if tranche == "june" then "2026-06-30T09:00:00Z" else "2026-05-31T09:00:00Z").get

  protected def docs(tranche: String): Vector[Document] =
    goldenSet.tranche(tranche).map(_.documents).getOrElse(fail(s"no tranche $tranche"))

  protected def fetch(path: String): ujson.Value =
    val answer = http.get(path)
    assertEquals(answer.status, 200, s"$path: ${answer.body}")
    answer.json

  /** The golden set's question under a prefix of the caller's; the id. */
  protected def seedQuestion(prefix: String): String =
    val question = goldenSet.question
    val id       = s"$prefix-${question.id}"
    val answer = http.post(
      "/questions",
      Obj(
        "id"        -> id,
        "statement" -> question.statement,
        "hypotheses" -> Arr.from(
          question.hypotheses.map(h => Obj("id" -> h.id, "statement" -> h.statement))
        ),
        "dated" -> "2026-05-01T00:00:00Z"
      )
    )
    assert(answer.status == 200 || answer.status == 201, answer.body)
    // The sources and the holders the service registered as it started.
    question.sources.foreach(source => fetch(s"/sources/${source.id}"): Unit)
    fetch(s"/holders/$LiveHolder"): Unit
    id

  /**
   * The appraiser at blueprint version 1 again, when a scenario before left the second instruction
   * set current, so the live run is of the instructions the feature names.
   */
  protected def restoreVersionOne(): Unit =
    val digest = Appraiser.blueprint(Instructions.named(Instructions.V1).get).digest
    val latest = fetch("/process/appraiser/versions")("versions").arr.last
    if latest("digest").str != digest then
      val answer = http.post("/process/appraiser/versions", Obj("instructions" -> Instructions.V1))
      assert(answer.status == 200 || answer.status == 201, answer.body)

  /** Starts a run of the appraiser and waits for it to end; the run id. */
  protected def runTranche(
      question: String,
      holder: String,
      tranche: String,
      id: String,
      follows: Option[String] = None
  ): String =
    clock.set(trancheDay(tranche))
    val input = Obj("tranche" -> tranche, "holder" -> holder, "question" -> question)
    follows.foreach(f => input("follows") = f)
    val answer =
      http.post("/process/runs", Obj("blueprint" -> Blueprint, "runId" -> id, "input" -> input))
    assert(answer.status == 200 || answer.status == 201, answer.body)
    // A real model reads every document of the tranche in turn, and Jev critiques every claim.
    Eventually.eventually(15.minutes) {
      assert(fetch(s"/process/runs/$id").obj.contains("ending"), s"the run $id has not ended")
    }
    id

  protected def ending(run: String): ujson.Value = fetch(s"/process/runs/$run")("ending")

  /**
   * The run ended `completed`, so its critique passed. When it did not, the failure says which
   * document's reading the model got wrong, found from the claim the critique refused through the
   * evidence it derives from.
   */
  protected def completed(run: String): Unit =
    val end = ending(run)
    if end("status").str != "completed" then
      val reason = end.obj.get("reason").map(_.str).getOrElse("")
      val claim  = "claim (\\S+) does not derive".r.findFirstMatchIn(reason).map(_.group(1))
      val document = claim
        .flatMap { id =>
          val held = http.get(s"/claims/$id")
          Option.when(held.status == 200)(held.json("claim")("derivesFrom").arr.headOption).flatten
        }
        .flatMap { evidence =>
          val held = http.get(s"/evidence/${evidence.str}")
          Option.when(held.status == 200)(held.json("locator").str)
        }
        .flatMap(locator => Vector("may", "june").flatMap(docs).find(_.locator == locator))
      document match
        case Some(doc) =>
          fail(
            s"the run $run failed its critique: the model's reading of the document " +
              s"'${doc.id}' (${doc.kind}, expected to ${doc.expectedReading.stance} " +
              s"${doc.expectedReading.hypothesis}) was refused: $reason"
          )
        case None => fail(s"the run $run ended ${end("status").str}: $reason")

  protected def revision(run: String): ujson.Value =
    fetch(s"/beliefs/revisions/${run}.yes")

  protected def acts(run: String): Vector[ujson.Value] =
    fetch(s"/process/runs/$run/acts")("acts").arr.toVector

  private def recordPath(kind: String, id: String): String = kind match
    case "evidence" => s"/evidence/$id"
    case "claim"    => s"/claims/$id"
    case "revision" => s"/beliefs/revisions/$id"
    case other      => fail(s"a run wrote a record of the kind $other")

  /**
   * Every record the run wrote is held, has an act naming the run, and is traced in the graph: the
   * run's answer from the graph names the same acts and records.
   */
  protected def everyRecordHeldWithItsAct(run: String, tranche: String): Unit =
    // The rows the acts are listed from may lag the writes by a moment.
    val written = Eventually.eventually(60.seconds) {
      val all   = acts(run)
      val kinds = all.groupBy(_("record")("kind").str).map((k, v) => k -> v.size)
      assertEquals(kinds.getOrElse("claim", 0), docs(tranche).size, all.mkString(", "))
      assertEquals(kinds.getOrElse("revision", 0), 1, all.mkString(", "))
      assert(kinds.getOrElse("evidence", 0) >= 1, all.mkString(", "))
      all
    }
    written.foreach { act =>
      val kind = act("record")("kind").str
      val id   = act("record")("id").str
      fetch(recordPath(kind, id)): Unit
      Eventually.eventually() {
        val byRecord = fetch(s"/process/acts?record=$kind:$id")("act")
        assertEquals(byRecord("id").str, act("id").str, s"$kind:$id")
        assertEquals(byRecord("run").str, run, s"$kind:$id")
      }
    }
    // The graph lags the service; the sink writes each node as its delta reaches it.
    Eventually.eventually(2.minutes) {
      val answer = fetch(s"/process/answers/run?run=$run")
      assertEquals(answer("run")("id").str, run)
      assertEquals(answer("ending")("status").str, "completed")
      val wrote = answer("wrote").arr.toVector.map(e => e("act")("id").str -> e("record")("id").str)
      assertEquals(wrote.toSet, written.map(a => a("id").str -> a("record")("id").str).toSet)
    }

  /** Every claim the run stated derives from evidence recorded from a document of the tranche. */
  protected def claimsDeriveFromTranche(run: String, tranche: String): Unit =
    val locators = docs(tranche).map(_.locator).toSet
    val claims = Eventually.eventually(60.seconds) {
      val claims = acts(run).filter(_("record")("kind").str == "claim")
      assertEquals(claims.size, docs(tranche).size, claims.mkString(", "))
      claims
    }
    claims.foreach { act =>
      val id = act("record")("id").str
      assert(id.startsWith(s"$run."), id)
      val derives = fetch(s"/claims/$id")("claim")("derivesFrom").arr.map(_.str)
      assert(derives.nonEmpty, s"the claim $id derives from no evidence")
      derives.foreach { evidence =>
        val locator = fetch(s"/evidence/$evidence")("locator").str
        assert(locators.contains(locator), s"the claim $id derives from $evidence at $locator")
      }
    }

object LiveAppraisal:
  private val counter = AtomicInteger(0)

  /** A prefix of the plain suite's own for each run of it in a JVM. */
  def prefix(): String = s"livesuite${counter.incrementAndGet()}"

  /** The two run ids of a holder's May and June runs under a prefix. */
  def runIds(prefix: String, holder: String): (String, String) =
    (s"$prefix-$holder-may", s"$prefix-$holder-june")
