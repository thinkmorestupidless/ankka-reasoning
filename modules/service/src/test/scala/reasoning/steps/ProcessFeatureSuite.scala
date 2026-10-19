package reasoning.steps

import com.thinkmorestupidless.ankka.agent.{AgentRuntime, Json}
import com.thinkmorestupidless.ankka.agent.blueprint.{RunContext, RunRef}
import reasoning.belief.application.SettableClock
import reasoning.belief.domain.{Evidence, Moment}
import reasoning.process.ProcessLayer
import reasoning.process.appraiser.{Appraiser, GoldenSet}
import reasoning.process.domain.Ids
import reasoning.process.tools.{RunReads, RunResolver, Tools}
import reasoning.service.ReasoningService
import reasoning.support.{Eventually, GraphFixture, Http, ServiceFixture}
import ujson.Obj

import scala.concurrent.duration.DurationInt

/**
 * A living feature of the process layer: runs of the appraiser over the golden set, driven by the
 * scripted model, reaching the service through `/process/runs` and the tools a run calls.
 *
 * @param graph
 *   whether the feature needs the graph; without it the suite speaks to the service alone
 */
abstract class ProcessFeatureSuite(feature: String, graph: Boolean = false)
    extends MarketFeatureSuite(feature):

  override protected def http: Http           = if graph then super.http else ServiceFixture.http
  override protected def clock: SettableClock = if graph then super.clock else ServiceFixture.clock

  /** ankka's agents of the fixture the feature runs against. */
  protected def agents: AgentRuntime = if graph then GraphFixture.agents else ServiceFixture.agents

  protected val goldenSet: GoldenSet = GoldenSet(ServiceFixture.settings.goldenSet)

  /** The process layer's nodes a scenario published, for a snapshot of the graph to take in. */
  override protected def extraNodeIds: Vector[String] =
    w.published.map((kind, id) => if kind == "act" then id else s"$kind:$id")

  override protected def beforeRebuild(): Unit =
    w.notes.get("run").foreach(run => w.notes("wrote") = wroteBy(run).body)

  protected val Blueprint = Appraiser.Name
  protected val HolderA   = "appraiser-a"
  protected val HolderB   = "appraiser-b"
  protected val Writer    = Ids.blueprintWriter(Appraiser.Name)
  protected val Sources   = goldenSet.question.sources.map(s => s.name -> s.id).toMap

  protected var script: ScriptedAppraiser = null

  override def beforeEach(context: BeforeEach): Unit =
    super.beforeEach(context)
    script = ScriptedAppraiser(ServiceFixture.model, ServiceFixture.judge, goldenSet)
    script.scenario = w.p.reverse.takeWhile(_.isDigit).reverse.toIntOption.getOrElse(0)

  // ── Runs ──────────────────────────────────────────────────────────────────

  protected def runId(name: String): String = s"${w.p}-$name"

  protected def runBody(
      tranche: String,
      holder: String,
      id: String,
      observedAt: Option[String] = None,
      follows: Option[String] = None
  ): Obj =
    val input = Obj("tranche" -> tranche, "holder" -> holder, "question" -> w.question)
    observedAt.foreach(at => input("observedAt") = at)
    follows.foreach(f => input("follows") = f)
    Obj("blueprint" -> Blueprint, "runId" -> id, "input" -> input)

  /** Starts a run and waits for it to end; the run id is the answer. */
  protected def run(
      tranche: String = "may",
      holder: String = HolderA,
      id: String = "",
      observedAt: Option[String] = None,
      follows: Option[String] = None
  ): String =
    val name = if id.isEmpty then w.fresh("run") else id
    ok(write("/process/runs", runBody(tranche, holder, name, observedAt, follows)))
    awaitEnded(name)
    w.notes("run") = name
    name

  protected def awaitEnded(id: String): ujson.Value =
    Eventually.eventually(90.seconds) {
      val answer = http.get(s"/process/runs/$id")
      assertEquals(answer.status, 200, answer.body)
      assert(answer.json.obj.contains("ending"), s"the run $id has not ended")
      answer.json
    }

  protected def ending(id: String): ujson.Value = held(s"/process/runs/$id")("ending")

  protected def acts(id: String): Vector[ujson.Value] =
    held(s"/process/runs/$id/acts")("acts").arr.toVector

  protected def actsOf(id: String, kind: String): Vector[ujson.Value] =
    acts(id).filter(_("record")("kind").str == kind)

  /** The act of a record, from the act rows, which may lag a write by a moment. */
  protected def actOf(kind: String, id: String): Option[ujson.Value] =
    held(s"/process/acts?record=$kind:$id").obj.get("act")

  protected def document(tranche: String, id: String) =
    goldenSet.document(tranche, id).getOrElse(fail(s"no document $tranche/$id"))

  protected def evidenceId(tranche: String, id: String): String =
    val doc = document(tranche, id)
    Evidence.digest(doc.source, doc.locator, script.excerpt(doc))

  protected def claimId(run: String, tranche: String, id: String): String =
    Ids.claim(run, script.statement(document(tranche, id)))

  /** The tools as the service built them, for a scenario that calls one directly. */
  protected lazy val tools: Tools =
    val recorder = ProcessLayer.recorder(
      ServiceFixture.kit.componentClient,
      clock,
      ReasoningService.beliefConfig(ServiceFixture.settings)
    )
    Tools(
      RunResolver(RunReads.of(ServiceFixture.agents), recorder, "service:reasoning"),
      recorder,
      goldenSet
    )

  // ── Background ────────────────────────────────────────────────────────────

  Given("the launch question with its hypotheses")(() => openQuestion(): Unit)

  Given(
    "the appraiser, registered at blueprint version 1, whose worker {string} speaks for the holder {string}"
  ) { (worker: String, holder: String) =>
    val versions = agents.blueprints.versions(Blueprint)
    assert(versions.exists(_.number == 1), versions.toString)
    assert(
      agents.blueprints.version(Blueprint, 1).workers.exists(_.name == worker),
      s"no worker $worker"
    )
    val record = held(s"/holders/$holder")
    assert(record("writers").arr.exists(_.str == Writer), record.render())
    w.published :+= "blueprint-version" -> Ids.version(Blueprint, 1)
  }

  Given("a scripted model")(() => script.install())

  // ── Evidence, claims and revisions ────────────────────────────────────────

  When(
    "a run of the appraiser records evidence from the source {string} with a locator, an excerpt and the time it was published"
  ) { (source: String) =>
    run()
    w.source = Sources(source)
    w.evidence = evidenceId("may", "filing")
  }

  Then("the evidence is held with its provenance") { () =>
    val doc      = document("may", "filing")
    val evidence = held(s"/evidence/${w.evidence}")
    assertEquals(evidence("source").str, doc.source)
    assertEquals(evidence("locator").str, doc.locator)
    assertEquals(evidence("excerpt").str, script.excerpt(doc))
    assertEquals(evidence("publishedAt").str, doc.publishedAt.iso)
  }

  Then("the evidence is held with the blueprint as the writer that sent it") { () =>
    assertEquals(held(s"/evidence/${w.evidence}")("writer").str, Writer)
  }

  Then(
    "an act is held naming the run, the step, the worker {string}, blueprint version {int}, the evidence and the holder {string}"
  ) { (worker: String, version: Int, holder: String) =>
    val act =
      held(s"/process/acts/${Ids.act(reasoning.process.domain.RecordRef("evidence", w.evidence))}")
    assertEquals(act("run").str, w.notes("run"))
    assertEquals(act("step").str, "read")
    assertEquals(act("worker").str, worker)
    assertEquals(act("blueprint").str, Blueprint)
    assertEquals(act("version").num.toInt, version)
    assertEquals(act("record")("kind").str, "evidence")
    assertEquals(act("record")("id").str, w.evidence)
    assertEquals(act("holder").str, holder)
    assertEquals(act("writer").str, Writer)
    w.notes("act") = act("id").str
  }

  Then("the act wrote that evidence and no other record") { () =>
    val byRecord = Eventually.eventually()(actOf("evidence", w.evidence).getOrElse(fail("no act")))
    assertEquals(byRecord("id").str, w.notes("act"))
    assertEquals(acts(w.notes("run")).count(_("id").str == w.notes("act")), 1)
  }

  Given("a run of the appraiser that recorded evidence") { () =>
    run()
    w.evidence = evidenceId("may", "filing")
  }

  When(
    "the run states the claim {string}, deriving from that evidence, contradicting {string}"
  ) { (statement: String, hypothesis: String) =>
    w.claim = Ids.claim(w.notes("run"), statement)
    w.notes("stance") = s"${w.hypotheses(hypothesis)} contradicts"
  }

  Then("the claim is held, stated by the holder {string}") { (holder: String) =>
    val claim = held(s"/claims/${w.claim}")("claim")
    assertEquals(claim("holder").str, holder)
    assertEquals(claim("writer").str, Writer)
    val stances = claim("stances").arr.map(s => s"${s("hypothesis").str} ${s("stance").str}")
    assertEquals(stances.toList, List(w.notes("stance")))
  }

  Then("the claim derives from that evidence") { () =>
    assertEquals(
      held(s"/claims/${w.claim}")("claim")("derivesFrom").arr.map(_.str).toList,
      List(w.evidence)
    )
  }

  Then("the claim is held with its act") { () =>
    val act = Eventually.eventually()(actOf("claim", w.claim).getOrElse(fail("no act")))
    assertEquals(act("run").str, w.notes("run"))
    assertEquals(act("worker").str, "reader")
  }

  Given("a run of the appraiser that stated two claims") { () =>
    run()
    w.claims =
      Vector(claimId(w.notes("run"), "may", "filing"), claimId(w.notes("run"), "may", "notice"))
  }

  When(
    "the run revises the belief of {string} in {string} to {string}, resting on both claims with weights"
  ) { (holder: String, hypothesis: String, probability: String) =>
    w.holder = holder
    w.revision = Ids.revision(
      w.notes("run"),
      reasoning.belief.domain.HypothesisRef.parse(w.hypotheses(hypothesis)).get
    )
    w.notes("probability") = probability
  }

  Then("the belief revision is held with its probability, the claims it rests on and each weight") {
    () =>
      val revision = held(s"/beliefs/revisions/${w.revision}")
      assertEqualsDouble(revision("probability").num, w.notes("probability").toDouble, 1e-9)
      val rests = revision("restsOn").arr.map(r => r("claim").str -> r("weight").num).toMap
      w.claims.foreach(c =>
        assert(rests.contains(c), s"the revision does not rest on $c: ${revision.render()}")
      )
      assertEqualsDouble(rests(w.claims(0)), script.weight(document("may", "filing")), 1e-9)
      assertEqualsDouble(rests(w.claims(1)), script.weight(document("may", "notice")), 1e-9)
  }

  Then("the belief revision is held by the holder {string}") { (holder: String) =>
    assertEquals(held(s"/beliefs/revisions/${w.revision}")("holder").str, holder)
  }

  Then("the belief revision is held with its act") { () =>
    val act = Eventually.eventually()(actOf("revision", w.revision).getOrElse(fail("no act")))
    assertEquals(act("run").str, w.notes("run"))
    assertEquals((act("step").str, act("worker").str), ("revise", "reviser"))
  }

  // ── An act that cannot be held ────────────────────────────────────────────

  Given("a run of the appraiser whose act cannot be held") { () =>
    // A run that is held, and a step of it with no single worker: the judgment's.
    run()
    w.notes("step") = "critique"
  }

  When("the run records evidence") { () =>
    val excerpt = s"a passage no run quotes, ${w.p}"
    val doc     = document("may", "filing")
    w.evidence = Evidence.digest(doc.source, doc.locator, excerpt)
    val answer = RunContext.within(RunRef(w.notes("run"), w.notes("step"), Blueprint, 1))(
      tools.recordEvidence.invoke(
        Json.obj("document" -> Json.str("filing"), "excerpt" -> Json.str(excerpt))
      )
    )
    w.notes("told") = answer.fold(identity, identity)
    w.notes("refused") = answer.isLeft.toString
  }

  Then("the evidence is not held")(() => notHeld(s"/evidence/${w.evidence}"))

  Then("the run is told the evidence was not held") { () =>
    assertEquals(w.notes("refused"), "true", w.notes("told"))
    assert(w.notes("told").contains("act.worker.unknown"), w.notes("told"))
  }

  // ── The rules a writer's record would break ───────────────────────────────

  When("a run of the appraiser records evidence from a source that is not registered") { () =>
    w.breaks = "evidence.source.not-held"
    w.notes("kind") = "evidence"
    w.evidence = evidenceId("stray", "unregistered")
    run(tranche = "stray")
  }

  When("a run of the appraiser states a claim deriving from no evidence") { () =>
    w.breaks = "claim.derives-from.none"
    w.notes("kind") = "claim"
    script.derivesFrom = Some(Vector.empty)
    val id = runId(w.fresh("run"))
    w.claim = claimId(id, "may", "filing")
    run(id = id)
  }

  When("a run of the appraiser states a claim with a stance on a hypothesis of another question") {
    () =>
      w.breaks = "claim.stance.hypothesis-not-held"
      w.notes("kind") = "claim"
      script.stance = Some(s"${w.fresh("other")}/yes supports")
      val id = runId(w.fresh("run"))
      w.claim = claimId(id, "may", "filing")
      run(id = id)
  }

  When("a run of the appraiser revises a belief resting on a claim of another holder") { () =>
    w.breaks = "belief.rests-on.other-holder"
    w.notes("kind") = "revision"
    w.holder = registerHolder()
    recordEvidence()
    val other = stateClaim()
    script.restsOn = Some(Vector(s"$other@0.5"))
    val id = runId(w.fresh("run"))
    w.revision = s"$id.yes"
    run(id = id)
  }

  When("a run of the appraiser revises a belief dated before the claim it rests on") { () =>
    w.breaks = "belief.dated.not-before-claim"
    w.notes("kind") = "revision"
    // An earlier run's claims, dated in July, which the critique fails so no revision follows.
    ServiceFixture.judge.reset()
    ServiceFixture.judge.always(
      com.thinkmorestupidless.ankka.agent.judgment.Answers.yesNo(Appraiser.question, 0.05)
    ): Unit
    val earlier = run(tranche = "june", holder = HolderB, observedAt = Some("2026-07-01T09:00:00Z"))
    assertEquals(ending(earlier)("status").str, "failed")
    ServiceFixture.judge.reset()
    ServiceFixture.judge.always(
      com.thinkmorestupidless.ankka.agent.judgment.Answers.yesNo(Appraiser.question, 0.95)
    ): Unit
    script.restsOn = Some(Vector(s"${claimId(earlier, "june", "approval")}@0.3"))
    val id = runId(w.fresh("run"))
    w.revision = s"$id.yes"
    run(holder = HolderB, id = id, observedAt = Some("2026-06-01T09:00:00Z"))
  }

  Then("the run is refused, naming the rule") { () =>
    assert(w.breaks.nonEmpty, "the step did not say which rule it set out to break")
    if w.last.status >= 400 then refusedBy(w.breaks)
    else
      val end = ending(w.notes("run"))
      assertEquals(end("status").str, "failed", end.render())
      assert(end("reason").str.contains(w.breaks), end("reason").str)
  }

  Then("nothing is held") { () =>
    w.notes.get("kind").foreach { kind =>
      w.notes.get("run").foreach(run => assertEquals(actsOf(run, kind), Vector.empty))
      kind match
        case "evidence" => notHeld(s"/evidence/${w.evidence}")
        case "claim"    => notHeld(s"/claims/${w.claim}")
        case "revision" => notHeld(s"/beliefs/revisions/${w.revision}")
        case _          => ()
    }
    w.notes.get("unstarted").foreach(id => notHeld(s"/process/runs/$id"))
  }

  // ── Who the blueprint speaks for ──────────────────────────────────────────

  When("a reader asks who speaks for the holder {string}") { (holder: String) =>
    w.last = http.get(s"/holders/$holder")
    w.holder = holder
  }

  Then("the reader is given the appraiser") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json("writers").arr.map(_.str).toList, List(Writer))
  }

  Then("the holder {string} is of the kind {string}") { (holder: String, kind: String) =>
    assertEquals(held(s"/holders/$holder")("kind").str, kind)
  }

  Given("the holder {string}, for which the appraiser does not speak") { (name: String) =>
    w.holder = holderId(name)
    ok(http.post("/holders", Obj("id" -> w.holder, "kind" -> "agent", "name" -> name))): Unit
  }

  When("a run of the appraiser states a claim as the holder {string}") { (name: String) =>
    val id = runId(w.fresh("run"))
    w.notes("unstarted") = id
    write("/process/runs", runBody("may", holderId(name), id)): Unit
  }

  Then("the run is refused, naming the holder") { () =>
    refusedBy("holder.writer.does-not-speak")
    assertEquals(w.last.status, 403)
    assertEquals(w.last.names.get("holder"), Some(w.holder), w.last.body)
  }

  // ── Repeats ───────────────────────────────────────────────────────────────

  Given(
    "a run of the appraiser under the run id {string} that recorded five pieces of evidence and stated five claims"
  ) { (id: String) =>
    run(id = runId(id))
    // The rows the acts are listed from may lag the writes by a moment.
    Eventually.eventually(60.seconds) {
      assertEquals(actsOf(w.notes("run"), "evidence").size, 5)
      assertEquals(actsOf(w.notes("run"), "claim").size, 5)
    }
  }

  When("the run is sent again under the run id {string}") { (id: String) =>
    val again = http.post("/process/runs", runBody("may", HolderA, runId(id)))
    assertEquals(again.status, 200, again.body)
    assertEquals(again.json("id").str, runId(id))
    awaitEnded(runId(id)): Unit
  }

  Then("five pieces of evidence, five claims and one belief revision are held") { () =>
    val all = Eventually.eventually(60.seconds) {
      val all = acts(w.notes("run"))
      assertEquals(all.size, 11, all.map(_("record")).mkString(", "))
      all
    }
    assertEquals(all.count(_("record")("kind").str == "evidence"), 5)
    assertEquals(all.count(_("record")("kind").str == "claim"), 5)
    assertEquals(all.count(_("record")("kind").str == "revision"), 1)
    assertEquals(all.size, 11)
  }

  Then("each has one act") { () =>
    val all = acts(w.notes("run"))
    assertEquals(
      all.map(a => (a("record")("kind").str, a("record")("id").str)).distinct.size,
      all.size
    )
    all.foreach { a =>
      val byRecord = actOf(a("record")("kind").str, a("record")("id").str)
      assertEquals(byRecord.map(_("id").str), Some(a("id").str))
    }
  }

  Given(
    "a run of the appraiser that recorded evidence from the source {string} with a locator and an excerpt"
  ) { (source: String) =>
    w.source = Sources(source)
    w.notes("earlier") = run()
    w.evidence = evidenceId("may", "notice")
  }

  When(
    "a later run of the appraiser records evidence from the same source with the same locator and the same excerpt"
  ) { () =>
    w.notes("later") = run(holder = HolderB)
    w.last = http.get(s"/evidence/${w.evidence}")
  }

  Then("the act of the evidence names the earlier run") { () =>
    assertEquals(actOf("evidence", w.evidence).map(_("run").str), Some(w.notes("earlier")))
    // The later run wrote no act for it: the record keeps the earlier run's.
    val later = actsOf(w.notes("later"), "evidence").map(_("record")("id").str)
    assertEquals(later.count(_ == w.evidence), 0)
  }

  Then("the claim the later run states from it has an act naming the later run") { () =>
    val claim = claimId(w.notes("later"), "may", "notice")
    assertEquals(
      held(s"/claims/$claim")("claim")("derivesFrom").arr.map(_.str).toList,
      List(w.evidence)
    )
    assertEquals(actOf("claim", claim).map(_("run").str), Some(w.notes("later")))
  }

  // ── No run ────────────────────────────────────────────────────────────────

  When("a writer of the service's own calls the tool that records evidence, in no run") { () =>
    val excerpt = s"a passage no run quotes, ${w.p}"
    val doc     = document("may", "filing")
    w.evidence = Evidence.digest(doc.source, doc.locator, excerpt)
    w.notes("kind") = "evidence"
    val answer = tools.recordEvidence.invoke(
      Json.obj("document" -> Json.str("filing"), "excerpt" -> Json.str(excerpt))
    )
    w.notes("told") = answer.fold(identity, identity)
    w.notes("refused") = answer.isLeft.toString
    w.notes.remove("run"): Unit
  }

  Then("the writer is told the tool was called in no run") { () =>
    assertEquals(w.notes("refused"), "true", w.notes("told"))
    assert(w.notes("told").contains("tool.called-in-no-run"), w.notes("told"))
  }

  // ── In the graph ──────────────────────────────────────────────────────────

  /** What a run wrote, as the graph answers it. */
  protected def wroteBy(run: String) = http.get(s"/process/answers/run?run=$run")

  private def millis(iso: String): Long = Moment.parse(iso).get.millis

  /** The acts of a run walked in the graph: each act's properties, the record and the holder. */
  private var walkedActs: Vector[(Map[String, Any], Option[String], Option[String])] = Vector.empty

  Given(
    "a run of the appraiser under the run id {string} over the May tranche, which has ended"
  ) { (id: String) =>
    val run = this.run(id = runId(id))
    // The rows the acts are listed from may lag the writes by a moment.
    val held = Eventually.eventually(60.seconds) {
      val all = acts(run)
      assertEquals(all.size, 11, all.map(_("record")).mkString(", "))
      all
    }
    w.published ++= Vector("run" -> run, "run-ending" -> run) ++
      held.map(a => "act" -> a("id").str) ++
      held.map(a => a("record")("kind").str -> a("record")("id").str)
  }

  Then(
    "the graph has a node for blueprint version {int} of the appraiser, with its name, its version and when it was registered"
  ) { (version: Int) =>
    val registered = agents.blueprints.versions(Blueprint).find(_.number == version).get
    val node = graphRows(
      "MATCH (v:BlueprintVersion {id: $id}) RETURN properties(v) AS v",
      "id" -> s"blueprint-version:${Ids.version(Blueprint, version)}"
    )(_.properties("v"))
    assertEquals(node.size, 1, node.toString)
    assertEquals(node.head("blueprint"), Blueprint)
    assertEquals(node.head("version"), version.toLong)
    assertEquals(node.head("digest"), registered.digest)
    assertEquals(node.head("dated"), registered.registeredAt)
  }

  Then(
    "the graph has a node for the run {string} with when it started, and an edge to the node of blueprint version {int}"
  ) { (id: String, version: Int) =>
    val run  = runId(id)
    val held = this.held(s"/process/runs/$run")("run")
    val node = graphRows(
      "MATCH (r:Run {id: $id}) RETURN properties(r) AS r, [(r)-[:OF]->(v) | v.id] AS versions",
      "id" -> s"run:$run"
    )(row => (row.properties("r"), row.strings("versions")))
    assertEquals(node.size, 1, node.toString)
    val (properties, versions) = node.head
    assertEquals(properties("blueprint"), Blueprint)
    assertEquals(properties("version"), version.toLong)
    assertEquals(properties("startedBy"), held("startedBy").str)
    assertEquals(properties("dated"), millis(held("dated").str))
    assertEquals(versions, Vector(s"blueprint-version:${Ids.version(Blueprint, version)}"))
  }

  Then(
    "the graph has a node for the run's run ending with the run status {string} and when it ended, and an edge to the run's node"
  ) { (status: String) =>
    val run  = w.notes("run")
    val held = ending(run)
    val node = graphRows(
      "MATCH (e:RunEnding {id: $id}) RETURN properties(e) AS e, [(e)-[:ENDED]->(r) | r.id] AS runs",
      "id" -> s"run-ending:$run"
    )(row => (row.properties("e"), row.strings("runs")))
    assertEquals(node.size, 1, node.toString)
    val (properties, runs) = node.head
    assertEquals(properties("status"), status)
    assertEquals(held("status").str, status)
    assertEquals(properties("dated"), millis(held("dated").str))
    assertEquals(runs, Vector(s"run:$run"))
  }

  When("a reader walks the edges from the node of each act of the run") { () =>
    walkedActs = graphRows(
      "MATCH (a:Act)-[:IN]->(:Run {id: $run}) " +
        "RETURN properties(a) AS act, [(a)-[:WROTE]->(x) | x.id][0] AS record, [(a)-[:AS]->(h) | h.id][0] AS holder",
      "run" -> s"run:${w.notes("run")}"
    )(row => (row.properties("act"), row.stringOption("record"), row.stringOption("holder")))
    assertEquals(walkedActs.size, 11, walkedActs.toString)
  }

  Then("each act's node has its step and its worker") { () =>
    val held = acts(w.notes("run")).map(a => a("id").str -> a).toMap
    walkedActs.foreach { (act, _, _) =>
      val id = act("id").toString
      assertEquals(act("step"), held(id)("step").str, id)
      assertEquals(act("worker"), held(id)("worker").str, id)
      assertEquals(act("recordKind"), held(id)("record")("kind").str, id)
    }
    assertEquals(walkedActs.map(_._1("id").toString).toSet, held.keySet)
  }

  Then(
    "each act's node has an edge to the run's node, an edge to the node of the record it wrote and an edge to the node of the holder {string}"
  ) { (holder: String) =>
    val held = acts(w.notes("run")).map(a => a("id").str -> a).toMap
    walkedActs.foreach { (act, record, holderNode) =>
      val id = act("id").toString
      assertEquals(
        record,
        Some(s"${held(id)("record")("kind").str}:${held(id)("record")("id").str}")
      )
      assertEquals(holderNode, Some(s"holder:$holder"))
    }
  }

  When("a reader walks the edges from the node of the current belief revision of {string}") {
    (holder: String) =>
      val current = held(s"/beliefs/current?holder=$holder&hypothesis=${w.hypothesis}")("current")
      w.revision = current("id").str
      val walked = graphRows(
        "MATCH (r:BeliefRevision {id: $revision})<-[:WROTE]-(a:Act)-[:IN]->(run:Run)-[:OF]->(v:BlueprintVersion) " +
          "RETURN a.id AS act, run.id AS run, v.id AS version",
        "revision" -> s"revision:${w.revision}"
      )(row => (row.string("act"), row.string("run"), row.string("version")))
      assertEquals(walked.size, 1, walked.toString)
      w.notes("act") = walked.head._1
      w.notes("walkedRun") = walked.head._2
      w.notes("version") = walked.head._3
  }

  Then(
    "the reader reaches the act that wrote it, the run {string} and blueprint version {int} of the appraiser"
  ) { (id: String, version: Int) =>
    val act = actOf("revision", w.revision).getOrElse(fail("the revision has no act"))
    assertEquals(w.notes("act"), act("id").str)
    assertEquals(w.notes("walkedRun"), s"run:${runId(id)}")
    assertEquals(w.notes("version"), s"blueprint-version:${Ids.version(Blueprint, version)}")
  }

  When("a reader asks what the run {string} wrote")((id: String) => w.last = wroteBy(runId(id)))

  Then(
    "the reader is given five pieces of evidence, five claims and one belief revision, each with its act"
  ) { () =>
    assertEquals(w.last.status, 200, w.last.body)
    val answer = w.last.json
    assertEquals(answer("run")("id").str, w.notes("run"))
    assertEquals(answer("ending")("status").str, "completed")
    val wrote = answer("wrote").arr.toVector
    val kinds = wrote.groupBy(_("record")("kind").str).map((k, v) => k -> v.size)
    assertEquals(kinds, Map("evidence" -> 5, "claim" -> 5, "revision" -> 1))
    val held = acts(w.notes("run")).map(a => a("id").str -> a).toMap
    wrote.foreach { entry =>
      val act = held.getOrElse(entry("act")("id").str, fail(s"an act not held: ${entry.render()}"))
      assertEquals(entry("record")("id").str, act("record")("id").str)
      assertEquals(entry("act")("step").str, act("step").str)
      assertEquals(entry("act")("worker").str, act("worker").str)
    }
  }

  When("a run of the appraiser records evidence and waits for the graph") { () =>
    run()
    w.evidence = evidenceId("may", "filing")
    w.source = document("may", "filing").source
    w.answers = Vector(
      http.post("/graph/wait", Obj("kind" -> "evidence", "id" -> w.evidence, "limitMs" -> 30000))
    )
    val act = Eventually.eventually()(actOf("evidence", w.evidence).getOrElse(fail("no act")))
    w.notes("act") = act("id").str
    w.answers :+= http.post(
      "/graph/wait",
      Obj("kind" -> "act", "id" -> w.notes("act"), "limitMs" -> 30000)
    )
    w.last = w.answers.last
  }

  Then(
    "the wait ends when the graph has a node for that evidence, an edge for each link it stated, a node for its act and the act's edges"
  ) { () =>
    w.answers.foreach { answer =>
      assertEquals(answer.status, 200, answer.body)
      assert(answer.json("caughtUp").bool, answer.body)
      assertEquals(answer.json("missing").arr.size, 0, answer.body)
    }
    val evidence = graphRows(
      "MATCH (e:Evidence {id: $id}) RETURN [(e)-[:FROM_SOURCE]->(s) | s.id] AS sources",
      "id" -> s"evidence:${w.evidence}"
    )(_.strings("sources"))
    assertEquals(evidence, Vector(Vector(s"source:${w.source}")))
    val act = graphRows(
      "MATCH (a:Act {id: $id}) RETURN [(a)-[:IN]->(r) | r.id] AS runs, [(a)-[:WROTE]->(x) | x.id] AS records, [(a)-[:AS]->(h) | h.id] AS holders",
      "id" -> w.notes("act")
    )(row => (row.strings("runs"), row.strings("records"), row.strings("holders")))
    assertEquals(
      act,
      Vector(
        (
          Vector(s"run:${w.notes("run")}"),
          Vector(s"evidence:${w.evidence}"),
          Vector(s"holder:$HolderA")
        )
      )
    )
  }

  Then(
    "the graph is as it was, the blueprint version, the run, its run ending and every act with it"
  ) { () =>
    val (nodes, edges) = snapshot()
    assertEquals(nodes.toVector.sorted.mkString("\n"), w.notes("nodes"))
    assertEquals(edges.toVector.sorted.mkString("\n"), w.notes("edges"))
    val ids = nodes.map(_.takeWhile(_ != ' '))
    val run = w.notes("run")
    assert(ids.contains(s"blueprint-version:${Ids.version(Blueprint, 1)}"), ids.toString)
    assert(ids.contains(s"run:$run"), ids.toString)
    assert(ids.contains(s"run-ending:$run"), ids.toString)
    assertEquals(ids.count(_.startsWith("act:")), 11)
    assertEquals(wroteBy(run).body, w.notes("wrote"))
  }
