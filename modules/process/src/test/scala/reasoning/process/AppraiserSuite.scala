package reasoning.process

import com.thinkmorestupidless.ankka.agent.Json
import com.thinkmorestupidless.ankka.agent.blueprint.{BlueprintCheck, Over, RunRef}
import com.thinkmorestupidless.ankka.testkit.TestTransport
import reasoning.belief.application.{BeliefConfig, Clock, Recorder}
import reasoning.belief.domain.Moment
import reasoning.process.application.ProcessRecorder
import reasoning.process.appraiser.{Appraiser, GoldenSet, Instructions}
import reasoning.process.tools.{RunReads, RunResolver, Tools}

/** The appraiser as data: ankka accepts both instruction sets, and the handlers decide as said. */
class AppraiserSuite extends munit.FunSuite:

  private val goldenSet = GoldenSet()
  private val clock     = new Clock { def now(): Moment = Moment(1_000L) }

  /** Tools over a client that answers nothing: the check reads their names, never calls them. */
  private val registry =
    val client = TestTransport().client
    val recorder = ProcessRecorder(
      client,
      clock,
      Recorder(client, clock, BeliefConfig(Set("agent"), 2000, Set.empty))
    )
    val reads = new RunReads:
      def run(id: String)                              = None
      def versionInfo(blueprint: String, version: Int) = None
      def version(blueprint: String, version: Int)     = None
    Appraiser.registry(
      Tools(RunResolver(reads, recorder, "service:reasoning"), recorder, goldenSet),
      goldenSet
    )

  private val ref = RunRef("r1", "load", "appraiser", 1)

  test("ankka accepts the appraiser at either instruction set, with no problems") {
    Instructions.all.foreach { set =>
      val (problems, _) = BlueprintCheck.check(Appraiser.blueprint(set), registry)
      assertEquals(problems, Vector.empty, s"instructions ${set.name}")
    }
  }

  test("the two instruction sets give two digests") {
    assertNotEquals(
      Appraiser.blueprint(Instructions.v1).digest,
      Appraiser.blueprint(Instructions.v2).digest
    )
  }

  test("the critique is a judgment over each reader item, and ankka accepts it") {
    val critique = Appraiser.blueprint(Instructions.v1).steps.find(_.name == "critique").get
    assert(critique.over.isInstanceOf[Over.Each], critique.over.toString)
    assertEquals(critique.namedQuestions, Vector(Appraiser.QuestionId))
  }

  test("load-tranche gives the tranche's documents without their readings, or fails the run") {
    val load = registry.handler("load-tranche").get
    val docs =
      load.run(ref, Json.obj("input" -> Json.obj("tranche" -> Json.str("may")))).asArray.get
    assertEquals(docs.size, 5)
    assertEquals(docs.head("id").flatMap(_.asString), Some("filing"))
    assertEquals(docs.head("expectedReading"), None)
    val failed = intercept[IllegalArgumentException](
      load.run(ref, Json.obj("input" -> Json.obj("tranche" -> Json.str("july"))))
    )
    assertEquals(failed.getMessage, "no tranche july")
  }

  private def item(claim: String): Json =
    Json.obj(
      "document" -> Json.str("filing"),
      "evidence" -> Json.str("e1"),
      "claim"    -> Json.str(claim)
    )

  private def judged(answer: String): Json =
    Json.obj(
      Appraiser.QuestionId -> Json.obj("answer" -> Json.str(answer), "probability" -> Json.num(0.9))
    )

  test("critique-verdict passes the claims the judgment answered yes to") {
    val verdict = registry.handler("critique-verdict").get
    val result = verdict.run(
      ref,
      Json.obj(
        "read"     -> Json.arr(item("r1.a"), item("r1.b")),
        "critique" -> Json.arr(judged("yes"), judged("yes"))
      )
    )
    assertEquals(result("passed").flatMap(_.asBoolean), Some(true))
    assertEquals(
      result("claims").flatMap(_.asArray).map(_.flatMap(_.asString)),
      Some(Vector("r1.a", "r1.b"))
    )
  }

  test("critique-verdict fails the run on the first claim judged no, naming it") {
    val verdict = registry.handler("critique-verdict").get
    val failed = intercept[IllegalStateException](
      verdict.run(
        ref,
        Json.obj(
          "read"     -> Json.arr(item("r1.a"), item("r1.b")),
          "critique" -> Json.arr(judged("yes"), judged("no"))
        )
      )
    )
    assertEquals(
      failed.getMessage,
      "claim r1.b does not derive from evidence recorded from a document of the tranche"
    )
  }

  test("critique-verdict fails the run on a reader that was refused, naming the rule") {
    val verdict = registry.handler("critique-verdict").get
    val refused = Json.obj(
      "document" -> Json.str("filing"),
      "refused" -> Json.obj(
        "rule"  -> Json.str("evidence.source.not-held"),
        "names" -> Json.obj("source" -> Json.str("nowhere"))
      )
    )
    val failed = intercept[IllegalStateException](
      verdict.run(ref, Json.obj("read" -> Json.arr(refused), "critique" -> Json.arr(judged("yes"))))
    )
    assertEquals(
      failed.getMessage,
      "the reader was refused: evidence.source.not-held, naming source=nowhere"
    )
  }

  test("revision-check passes the revision on, or fails the run on the reviser's refusal") {
    val close = registry.handler("revision-check").get
    assertEquals(
      close.run(ref, Json.obj("revise" -> Json.obj("revision" -> Json.str("r1.yes")))),
      Json.obj("revision" -> Json.str("r1.yes"))
    )
    val failed = intercept[IllegalStateException](
      close.run(
        ref,
        Json.obj(
          "revise" -> Json.obj(
            "refused" -> Json.obj(
              "rule"  -> Json.str("belief.rest.format"),
              "names" -> Json.obj("rest" -> Json.str("c@x"))
            )
          )
        )
      )
    )
    assertEquals(failed.getMessage, "the reviser was refused: belief.rest.format, naming rest=c@x")
  }
