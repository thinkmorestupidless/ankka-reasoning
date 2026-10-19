package reasoning.process

import com.thinkmorestupidless.ankka.agent.{Json, TokenUsage}
import com.thinkmorestupidless.ankka.agent.blueprint.{
  Blueprint,
  RunContext,
  RunRef,
  RunSnapshot,
  RunStatus as AnkkaStatus,
  VersionInfo
}
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import reasoning.belief.BeliefLayer
import reasoning.belief.application.*
import reasoning.belief.domain.{Evidence, Moment}
import reasoning.process.application.*
import reasoning.process.appraiser.{Appraiser, GoldenSet, Instructions}
import reasoning.process.domain.{Ids, RecordRef}
import reasoning.process.tools.{RunReads, RunResolver, Tools}

import scala.concurrent.duration.DurationInt

/**
 * The three tools against the belief layer's entities and this layer's, with ankka's runs stubbed:
 * a record is held with its act, or nothing is held at all.
 */
class ToolsSuite extends munit.FunSuite:

  override val munitTimeout = 3.minutes

  private val clock  = SettableClock(Moment.parse("2026-12-01T00:00:00Z").get)
  private val writer = "service:reasoning"
  private val Held   = "tools-run-held"

  private lazy val kit = AnkkaTestKit.start(
    BeliefLayer.components ++ Seq(
      BlueprintVersionEntity.descriptor,
      RunEntityRecord.descriptor,
      RunEndingEntity.descriptor,
      ActEntity.descriptor
    )
  )

  override def afterAll(): Unit = kit.stop()

  private lazy val recorder = ProcessRecorder(
    kit.componentClient,
    clock,
    Recorder(kit.componentClient, clock, BeliefConfig(Set("agent"), 2000, Set.empty))
  )

  private val goldenSet = GoldenSet()

  private val input = Json.obj(
    "tranche"  -> Json.str("may"),
    "holder"   -> Json.str("appraiser-a"),
    "question" -> Json.str("launch")
  )

  /** ankka holds one run, of the appraiser at version 1. */
  private val reads = new RunReads:
    def run(id: String): Option[RunSnapshot] = Option.when(id == Held)(
      RunSnapshot(
        Held,
        "appraiser",
        1,
        input,
        AnkkaStatus.Running,
        Some("read"),
        Vector.empty,
        TokenUsage(0, 0, 0, 0),
        TokenUsage(0, 0, 0, 0),
        0,
        "service:test/caller",
        1_700_000_000_000L,
        None,
        None
      )
    )
    def versionInfo(blueprint: String, version: Int): Option[VersionInfo] =
      Option.when(blueprint == "appraiser" && version == 1)(
        VersionInfo(1, "d" * 64, 1_600_000_000_000L)
      )
    def version(blueprint: String, version: Int): Option[Blueprint] =
      Option.when(blueprint == "appraiser" && version == 1)(Appraiser.blueprint(Instructions.v1))

  private lazy val tools =
    val t = Tools(RunResolver(reads, recorder, writer), recorder, goldenSet)
    recorder.belief.openQuestion(
      OpenQuestion(
        "launch",
        "Will Company X launch Product Y this year?",
        Vector(
          HypothesisRequest("yes", "it launches this year"),
          HypothesisRequest("no", "it does not launch this year")
        ),
        Some(Moment.parse("2026-05-01T09:00:00Z").get)
      ),
      "local"
    )
    recorder.belief.registerHolder(
      RegisterHolder("appraiser-a", "agent", "appraiser-a"),
      Ids.blueprintWriter("appraiser")
    )
    goldenSet.question.sources.foreach(s =>
      recorder.belief.registerSource(RegisterSource(s.id, s.name), writer)
    )
    t

  private def inRun(step: String)(body: => Either[String, String]): Either[String, String] =
    RunContext.within(RunRef(Held, step, "appraiser", 1))(body)

  private def parsed(answer: Either[String, String]): Json =
    Json.parse(answer.fold(e => fail(s"the tool refused: $e"), identity)).fold(fail(_), identity)

  private def id(json: Json, kind: String): String =
    json(kind).flatMap(_("id")).flatMap(_.asString).get

  private def evidenceArgs(excerpt: String) =
    Json.obj("document" -> Json.str("filing"), "excerpt" -> Json.str(excerpt))

  test("evidence a run records is held with its act; the version and the run are held first") {
    val answer =
      parsed(inRun("read")(tools.recordEvidence.invoke(evidenceArgs("approval is pending"))))
    val evidence = id(answer, "evidence")
    assertEquals(recorder.belief.findEvidence(evidence).map(_.source), Some("company-x-filings"))
    assertEquals(recorder.belief.findEvidence(evidence).map(_.writer), Some("blueprint:appraiser"))
    val act =
      recorder.findAct(Ids.act(RecordRef(RecordRef.Evidence, evidence))).getOrElse(fail("no act"))
    assertEquals(
      (act.run, act.step, act.worker, act.blueprint, act.version, act.holder),
      (Held, "read", "reader", "appraiser", 1, "appraiser-a")
    )
    assertEquals(id(answer, "act"), act.id)
    assertEquals(recorder.findVersion("appraiser", 1).map(_.digest), Some("d" * 64))
    assertEquals(
      recorder.findRun(Held).map(r => (r.startedBy, r.dated)),
      Some(("service:test/caller", Moment(1_700_000_000_000L)))
    )
  }

  test("a claim and a belief revision a run writes are held with their acts") {
    val evidence = id(
      parsed(
        inRun("read")(tools.recordEvidence.invoke(evidenceArgs("the launch date is uncertain")))
      ),
      "evidence"
    )
    val claim = parsed(
      inRun("read")(
        tools.stateClaim.invoke(
          Json.obj(
            "statement"   -> Json.str("approval is pending and the launch date is uncertain"),
            "derivesFrom" -> Json.arr(Json.str(evidence)),
            "stances"     -> Json.arr(Json.str("launch/yes contradicts"))
          )
        )
      )
    )
    val claimId = id(claim, "claim")
    assertEquals(claimId, Ids.claim(Held, "approval is pending and the launch date is uncertain"))
    assertEquals(recorder.belief.findClaim(claimId).map(_.holder), Some("appraiser-a"))
    assertEquals(
      recorder.findAct(Ids.act(RecordRef(RecordRef.Claim, claimId))).map(_.run),
      Some(Held)
    )
    val revision = parsed(
      inRun("revise")(
        tools.reviseBelief.invoke(
          Json.obj(
            "hypothesis"  -> Json.str("launch/yes"),
            "probability" -> Json.num(0.35),
            "restsOn"     -> Json.arr(Json.str(s"$claimId@0.25"))
          )
        )
      )
    )
    val revisionId = id(revision, "revision")
    assertEquals(revisionId, s"$Held.yes")
    assertEquals(
      recorder.belief.findRevision(revisionId).map(_.restsOn.flatMap(_.weight)),
      Some(Vector(0.25))
    )
    assertEquals(
      recorder.findAct(Ids.act(RecordRef(RecordRef.Revision, revisionId))).map(_.worker),
      Some("reviser")
    )
  }

  test("a repeat writes nothing twice: the same answer, the same act") {
    val first =
      inRun("read")(tools.recordEvidence.invoke(evidenceArgs("distributors are being briefed")))
    val second =
      inRun("read")(tools.recordEvidence.invoke(evidenceArgs("distributors are being briefed")))
    assert(first.isRight, first.toString)
    assertEquals(second, first)
  }

  test("nothing is held when the step has no single worker") {
    val excerpt = "the regulator's decision may take until late October"
    val answer  = inRun("critique")(tools.recordEvidence.invoke(evidenceArgs(excerpt)))
    assert(answer.left.exists(_.contains("act.worker.unknown")), answer.toString)
    val doc = goldenSet.document("may", "filing").get
    assertEquals(
      recorder.belief.findEvidence(Evidence.digest(doc.source, doc.locator, excerpt)),
      None
    )
  }

  test("nothing is held when ankka holds no such run") {
    val excerpt = "an analyst models the launch next year"
    val answer = RunContext.within(RunRef("tools-run-unknown", "read", "appraiser", 1))(
      tools.recordEvidence.invoke(evidenceArgs(excerpt))
    )
    assert(answer.left.exists(_.contains("run.not-held")), answer.toString)
    val doc = goldenSet.document("may", "filing").get
    assertEquals(
      recorder.belief.findEvidence(Evidence.digest(doc.source, doc.locator, excerpt)),
      None
    )
    assertEquals(recorder.findRun("tools-run-unknown"), None)
  }

  test("a tool outside any run writes nothing") {
    val excerpt = "the sensor supplier will be at volume"
    val answer  = tools.recordEvidence.invoke(evidenceArgs(excerpt))
    assert(answer.left.exists(_.contains("tool.called-in-no-run")), answer.toString)
    val doc = goldenSet.document("may", "filing").get
    assertEquals(
      recorder.belief.findEvidence(Evidence.digest(doc.source, doc.locator, excerpt)),
      None
    )
  }

  test("a document the tranche does not hold, and a rest that is not a claim and a weight") {
    val missing = inRun("read")(
      tools.recordEvidence.invoke(
        Json.obj("document" -> Json.str("approval"), "excerpt" -> Json.str("x"))
      )
    )
    assert(missing.left.exists(_.contains("document.not-in-tranche")), missing.toString)
    val rest = inRun("revise")(
      tools.reviseBelief.invoke(
        Json.obj(
          "hypothesis"  -> Json.str("launch/yes"),
          "probability" -> Json.num(0.5),
          "restsOn"     -> Json.arr(Json.str("c@heavy"))
        )
      )
    )
    assert(rest.left.exists(_.contains("belief.rest.format")), rest.toString)
  }
