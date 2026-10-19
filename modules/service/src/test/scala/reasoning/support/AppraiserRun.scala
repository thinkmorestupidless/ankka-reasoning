package reasoning.support

import reasoning.process.appraiser.GoldenSet
import reasoning.steps.{FeatureSuite, ScriptedAppraiser}
import ujson.{Arr, Obj}

import scala.concurrent.duration.DurationInt

/** A run of the appraiser that has ended, with its acts as the service lists them. */
final case class CompletedRun(id: String, question: String, acts: Vector[ujson.Value]):
  def revision: String = s"$id.yes"

/**
 * The appraiser run from a plain suite: a question of the suite's own, the scripted model, one run
 * over a tranche, and the graph caught up with everything the run wrote.
 */
object AppraiserRun:

  private val goldenSet = GoldenSet(ServiceFixture.settings.goldenSet)

  private def check(condition: Boolean, message: => String): Unit =
    if !condition then throw new AssertionError(message)

  /**
   * Opens the launch question under `prefix`, runs the appraiser over the tranche as the holder and
   * waits for the run to end completed and for the graph to hold the version, the run, its ending,
   * every act and every record.
   */
  def completed(
      http: Http,
      prefix: String,
      holder: String = "appraiser-a",
      tranche: String = "may"
  ): CompletedRun =
    ServiceFixture.model.reset()
    ServiceFixture.judge.reset()
    val script = ScriptedAppraiser(ServiceFixture.model, ServiceFixture.judge, goldenSet)
    script.scenario = FeatureSuite.next()
    script.install()

    val question = s"$prefix-launch"
    val opened = http.post(
      "/questions",
      Obj(
        "id"        -> question,
        "statement" -> "Will Company X launch Product Y this year?",
        "hypotheses" -> Arr(
          Obj("id" -> "yes", "statement" -> "it launches this year"),
          Obj("id" -> "no", "statement"  -> "it does not launch this year")
        ),
        "dated" -> "2026-05-01T09:00:00Z"
      )
    )
    check(opened.status == 201 || opened.status == 200, opened.body)

    val run = s"$prefix-$tranche"
    val started = http.post(
      "/process/runs",
      Obj(
        "blueprint" -> "appraiser",
        "runId"     -> run,
        "input"     -> Obj("tranche" -> tranche, "holder" -> holder, "question" -> question)
      )
    )
    check(started.status == 201, started.body)
    val ended = Eventually.eventually(90.seconds) {
      val answer = http.get(s"/process/runs/$run")
      check(answer.status == 200, answer.body)
      check(answer.json.obj.contains("ending"), s"the run $run has not ended")
      answer.json("ending")
    }
    check(ended("status").str == "completed", ended.render())
    // The rows the acts are listed from may lag the writes by a moment.
    val acts = Eventually.eventually(60.seconds) {
      val all = http.get(s"/process/runs/$run/acts").json("acts").arr.toVector
      check(all.size == 11, s"${all.size} acts: ${all.map(_("record")).mkString(", ")}")
      all
    }
    val published =
      Vector("blueprint-version" -> "appraiser@1", "run" -> run, "run-ending" -> run) ++
        acts.map(a => "act" -> a("id").str) ++
        acts.map(a => a("record")("kind").str -> a("record")("id").str)
    published.foreach { (kind, id) =>
      val wait = http.post("/graph/wait", Obj("kind" -> kind, "id" -> id, "limitMs" -> 30000))
      check(wait.status == 200, wait.body)
      check(
        wait.json("caughtUp").bool,
        s"the graph did not catch up with $kind '$id': ${wait.body}"
      )
    }
    CompletedRun(run, question, acts)
