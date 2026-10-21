package reasoning

import com.thinkmorestupidless.ankka.agent.Json
import reasoning.belief.domain.{Evidence, Moment}
import reasoning.process.ProcessLayer
import reasoning.process.appraiser.{GoldenSet, Instructions}
import reasoning.process.tools.{RunReads, RunResolver, Tools}
import reasoning.seed.Seed
import reasoning.service.ReasoningService
import reasoning.steps.ScriptedAppraiser
import reasoning.support.{Answer, Eventually, GraphFixture, ServiceFixture}
import ujson.Obj

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration.DurationInt

/**
 * The replies the documentation shows for the process layer, taken from the service itself: the
 * golden set seeded, the four runs with the scripted model, the answers with acts, the versions,
 * the refusals and a trace from the graph database. Each is written to the directory
 * `REASONING_CAPTURE_DIR` names, under clean identifiers, and the suite is skipped without one.
 */
class DocsCaptureSuite extends munit.FunSuite:

  override val munitTimeout = 20.minutes

  private val dir: Option[Path] = sys.env.get("REASONING_CAPTURE_DIR").map(Paths.get(_))

  private def http = GraphFixture.http

  private def capture(name: String, request: String, answer: Answer): ujson.Value =
    val body =
      if answer.body.isEmpty then "" else ujson.write(answer.json, indent = 2)
    Files.writeString(dir.get.resolve(s"$name.txt"), s"$request\nHTTP ${answer.status}\n$body\n")
    if answer.body.isEmpty then ujson.Null else answer.json

  private def get(name: String, path: String): ujson.Value =
    capture(name, s"GET $path", http.get(path))

  private def post(name: String, path: String, body: Obj): ujson.Value =
    capture(name, s"POST $path\n${ujson.write(body, indent = 2)}", http.post(path, body))

  /**
   * A write as the seed client sends it: with no writer header, as the seed file's records were.
   */
  private def postAsSeed(name: String, path: String, body: Obj): ujson.Value =
    val sent = Seed.send(Vector(Obj("post" -> path, "body" -> body)), GraphFixture.baseUrl).head
    capture(name, s"POST $path\n${ujson.write(body, indent = 2)}", Answer(sent.status, sent.body))

  private def awaitEnded(id: String): ujson.Value =
    Eventually.eventually(5.minutes) {
      val answer = http.get(s"/process/runs/$id")
      assertEquals(answer.status, 200, answer.body)
      assert(answer.json.obj.contains("ending"), s"the run $id has not ended")
      answer.json
    }

  private def cypher(name: String, query: String): Unit =
    val out = GraphFixture.neo4j.execInContainer(
      "cypher-shell",
      "-u",
      "neo4j",
      "-p",
      GraphFixture.Password,
      "--format",
      "plain",
      query
    )
    Files.writeString(
      dir.get.resolve(s"$name.txt"),
      s"$query\n---\n${out.getStdout}${out.getStderr}"
    ): Unit

  test("the process layer's replies for the pages") {
    assume(dir.isDefined, "REASONING_CAPTURE_DIR is not set")
    Files.createDirectories(dir.get)
    val goldenSet = GoldenSet(ServiceFixture.settings.goldenSet)
    ServiceFixture.model.reset()
    ServiceFixture.judge.reset()
    val script = ScriptedAppraiser(ServiceFixture.model, ServiceFixture.judge, goldenSet)
    script.install()
    val clock = GraphFixture.clock
    try
      // The seed file, as `just seed golden-set` sends it.
      clock.set(Moment.parse("2026-05-02T10:00:00Z").get)
      val sent = Seed.run(Paths.get("golden-set", "seed.json"), GraphFixture.baseUrl)
      Files.writeString(
        dir.get.resolve("seed.txt"),
        sent
          .map(s => f"${s.status}%3d ${s.path}%-28s ${if s.refused then s.body else ""}")
          .mkString("\n") +
          s"\n${sent.size} sent, ${sent.count(_.created)} new, ${sent.count(_.status == 200)} already held\n"
      )
      get("versions-at-start", "/process/appraiser/versions")

      // appraiser-a over May, then June.
      clock.set(Moment.parse("2026-05-31T09:00:00Z").get)
      post(
        "run-may-start",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "appraiser-a-may",
          "input" -> Obj(
            "tranche"    -> "may",
            "holder"     -> "appraiser-a",
            "question"   -> "launch",
            "observedAt" -> "2026-05-31T09:00:00Z"
          )
        )
      )
      awaitEnded("appraiser-a-may")
      Eventually.eventually(60.seconds) {
        assertEquals(http.get("/process/runs/appraiser-a-may/acts").json("acts").arr.size, 11)
      }
      get("run-may", "/process/runs/appraiser-a-may")
      get("run-may-acts", "/process/runs/appraiser-a-may/acts")
      post(
        "run-may-again",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "appraiser-a-may",
          "input" -> Obj(
            "tranche"    -> "may",
            "holder"     -> "appraiser-a",
            "question"   -> "launch",
            "observedAt" -> "2026-05-31T09:00:00Z"
          )
        )
      )
      clock.set(Moment.parse("2026-06-30T09:00:00Z").get)
      post(
        "run-june-start",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "appraiser-a-june",
          "input" -> Obj(
            "tranche"    -> "june",
            "holder"     -> "appraiser-a",
            "question"   -> "launch",
            "follows"    -> "appraiser-a-may.yes",
            "observedAt" -> "2026-06-30T09:00:00Z"
          )
        )
      )
      awaitEnded("appraiser-a-june")
      Eventually.eventually(60.seconds) {
        assertEquals(http.get("/process/runs/appraiser-a-june/acts").json("acts").arr.size, 7)
      }
      get("run-june", "/process/runs/appraiser-a-june")
      val juneActs = get("run-june-acts", "/process/runs/appraiser-a-june/acts")("acts").arr
      val approvalClaim = juneActs
        .find(a => a("record")("kind").str == "claim" && a("record")("id").str.nonEmpty)
        .map(_("record")("id").str)
        .get
      get("act-by-record", s"/process/acts?record=claim:$approvalClaim")
      val actId = juneActs.head("id").str
      get("act-by-id", s"/process/acts/$actId")
      post(
        "wait-revision",
        "/graph/wait",
        Obj("kind" -> "revision", "id" -> "appraiser-a-june.yes", "limitMs" -> 30000)
      )
      Eventually.eventually(60.seconds) {
        val answer = http.get("/process/answers/run?run=appraiser-a-june")
        assertEquals(answer.status, 200, answer.body)
        assertEquals(answer.json("wrote").arr.size, 7, answer.body)
      }
      get("answer-run", "/process/answers/run?run=appraiser-a-june")
      get(
        "answer-belief-change",
        "/process/answers/belief-change?from=appraiser-a-may.yes&to=appraiser-a-june.yes"
      )
      get("answer-belief", "/process/answers/belief?holder=appraiser-a&hypothesis=launch/yes")
      get("answer-case", "/process/answers/case?hypothesis=launch/yes&stance=supports")
      get(
        "answer-belief-asof",
        "/process/answers/belief?holder=appraiser-a&hypothesis=launch/yes&asOf=2026-06-10T00:00:00Z"
      )
      get("answer-learned", "/process/answers/learned?question=launch&after=2026-06-01T00:00:00Z")

      // The second instruction set, and appraiser-b.
      post("version-register", "/process/appraiser/versions", Obj("instructions" -> "v2"))
      post("version-register-again", "/process/appraiser/versions", Obj("instructions" -> "v2"))
      post("version-unknown", "/process/appraiser/versions", Obj("instructions" -> "v3"))
      val versions = get("versions", "/process/appraiser/versions")("versions").arr
      val v2       = versions.last("version").num.toInt
      // The version's record is copied from ankka's event, which arrives a moment after the reply.
      Eventually.eventually() {
        assertEquals(
          get("version-detail", s"/process/appraiser/versions/$v2")("version").num.toInt,
          v2
        )
      }
      script.instructions = Instructions.V2
      clock.set(Moment.parse("2026-05-31T09:00:00Z").get)
      post(
        "run-b-may-start",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "appraiser-b-may",
          "input" -> Obj(
            "tranche"    -> "may",
            "holder"     -> "appraiser-b",
            "question"   -> "launch",
            "observedAt" -> "2026-05-31T09:00:00Z"
          )
        )
      )
      awaitEnded("appraiser-b-may")
      clock.set(Moment.parse("2026-06-30T09:00:00Z").get)
      post(
        "run-b-june-start",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "appraiser-b-june",
          "input" -> Obj(
            "tranche"    -> "june",
            "holder"     -> "appraiser-b",
            "question"   -> "launch",
            "follows"    -> "appraiser-b-may.yes",
            "observedAt" -> "2026-06-30T09:00:00Z"
          )
        )
      )
      awaitEnded("appraiser-b-june")
      get("run-b-june", "/process/runs/appraiser-b-june")
      post(
        "wait-revision-b",
        "/graph/wait",
        Obj("kind" -> "revision", "id" -> "appraiser-b-june.yes", "limitMs" -> 30000)
      )
      Eventually.eventually(60.seconds) {
        val answer =
          http.get("/process/answers/comparison?hypothesis=launch/yes&a=appraiser-a&b=appraiser-b")
        assertEquals(answer.status, 200, answer.body)
        // Both holders' acts, the second's version 2 among them; the acts lag the records a moment.
        val runs = answer.json("acts").arr.map(_("run")("id").str).toSet
        assert(runs == Set("appraiser-a-june", "appraiser-b-june"), answer.body)
        // Three claims a side, the three pieces of evidence once, and the two revisions.
        assertEquals(answer.json("acts").arr.size, 11, answer.body)
      }
      get(
        "answer-comparison",
        "/process/answers/comparison?hypothesis=launch/yes&a=appraiser-a&b=appraiser-b"
      )

      // The market resolved afterwards.
      clock.set(Moment.parse("2026-11-14T12:00:00Z").get)
      val approval = goldenSet.document("june", "approval").get
      val approvalEvidence =
        Evidence.digest(approval.source, approval.locator, script.excerpt(approval))
      postAsSeed(
        "resolve",
        "/markets/launch-market/resolutions",
        Obj(
          "id"        -> "announced",
          "outcome"   -> "YES",
          "evidence"  -> ujson.Arr(approvalEvidence),
          "authority" -> "Company X announcement",
          "dated"     -> "2026-11-14T12:00:00Z"
        )
      )
      post(
        "wait-market",
        "/graph/wait",
        Obj("kind" -> "market", "id" -> "launch-market", "limitMs" -> 30000)
      )
      Eventually.eventually(60.seconds) {
        val answer = http.get("/process/markets/launch-market/beliefs-at-resolution")
        assertEquals(answer.status, 200, answer.body)
        assert(answer.json("acts").arr.nonEmpty, answer.body)
      }
      get("answer-beliefs-at-resolution", "/process/markets/launch-market/beliefs-at-resolution")
      get("answer-resolution", "/process/markets/launch-market/resolution")

      // Refusals.
      post(
        "refusal-blueprint-not-held",
        "/process/runs",
        Obj(
          "blueprint" -> "forecaster",
          "runId"     -> "forecaster-1",
          "input"     -> Obj("tranche" -> "may")
        )
      )
      post(
        "refusal-input-invalid",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "appraiser-a-july",
          "input"     -> Obj("tranche" -> "july", "holder" -> "appraiser-a")
        )
      )
      post(
        "refusal-run-id-held",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "appraiser-a-may",
          "input"     -> Obj("tranche" -> "june", "holder" -> "appraiser-a", "question" -> "launch")
        )
      )
      post(
        "refusal-does-not-speak",
        "/process/runs",
        Obj(
          "blueprint" -> "appraiser",
          "runId"     -> "agent-a-may",
          "input"     -> Obj("tranche" -> "may", "holder" -> "agent-a", "question" -> "launch")
        )
      )
      get("refusal-record-format", "/process/acts?record=appraiser-a-may")
      get("refusal-run-not-held", "/process/runs/appraiser-c-may")
      val recorder = ProcessLayer.recorder(
        GraphFixture.kit.componentClient,
        clock,
        ReasoningService.beliefConfig(GraphFixture.settings)
      )
      val tools = Tools(
        RunResolver(RunReads.of(GraphFixture.agents), recorder, "service:reasoning"),
        recorder,
        goldenSet
      )
      val told = tools.recordEvidence.invoke(
        Json.obj("document" -> Json.str("filing"), "excerpt" -> Json.str("a passage no run quotes"))
      )
      Files.writeString(
        dir.get.resolve("refusal-tool-no-run.txt"),
        s"record_evidence {document: filing, excerpt: ...} outside any run\n---\n${told.fold(identity, identity)}\n"
      )

      // The vocabulary's process layer, and the graph.
      val vocabulary = http.get("/graph/vocabulary").json
      val process    = vocabulary("layers").arr.find(_("name").str == "process").get
      Files.writeString(dir.get.resolve("vocabulary-process.txt"), ujson.write(process, indent = 2))
      cypher(
        "cypher-trace",
        "MATCH (r:Element {id: 'revision:appraiser-a-june.yes'})<-[:WROTE]-(a:Act)-[:IN]->(run:Run)-[:OF]->(v:BlueprintVersion) RETURN a.step, a.worker, run.id, v.version"
      )
      cypher(
        "cypher-run",
        "MATCH (a:Act)-[:IN]->(run:Element {id: 'run:appraiser-a-june'}) MATCH (a)-[:WROTE]->(x) RETURN a.step, a.worker, a.recordKind, x.id ORDER BY x.id"
      )
      cypher(
        "cypher-ending",
        "MATCH (e:RunEnding)-[:ENDED]->(run:Run) RETURN run.id, run.version, e.status, datetime({epochMillis: run.dated}) AS started, datetime({epochMillis: e.dated}) AS ended ORDER BY run.id"
      )
      cypher(
        "cypher-versions",
        "MATCH (run:Run)-[:OF]->(v:BlueprintVersion) MATCH (a:Act)-[:IN]->(run) MATCH (a)-[:WROTE]->(r:BeliefRevision) RETURN r.id, r.probability, v.version ORDER BY r.id"
      )
      cypher(
        "cypher-holder",
        "MATCH (a:Act)-[:AS]->(h:Holder) RETURN h.id, count(a) AS acts ORDER BY h.id"
      )
    finally clock.set(ServiceFixture.Start)
  }
