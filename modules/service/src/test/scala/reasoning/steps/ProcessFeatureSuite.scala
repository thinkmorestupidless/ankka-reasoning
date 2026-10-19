package reasoning.steps

import com.thinkmorestupidless.ankka.agent.{AgentRuntime, Json}
import com.thinkmorestupidless.ankka.agent.blueprint.{RunContext, RunRef}
import reasoning.belief.application.SettableClock
import reasoning.belief.domain.{Evidence, Moment}
import reasoning.process.ProcessLayer
import reasoning.process.appraiser.{Appraiser, Document, GoldenSet, Instructions}
import reasoning.process.domain.Ids
import reasoning.process.tools.{RunReads, RunResolver, Tools}
import reasoning.service.{Providers, ReasoningService}
import reasoning.support.{Answer, Eventually, GraphFixture, Http, ServiceFixture}
import ujson.{Arr, Obj}

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

  /** The appraiser's holders are the service's own, under no scenario's prefix. */
  override protected def holderId(name: String): String =
    if name == HolderA || name == HolderB then name else super.holderId(name)

  /**
   * In the golden set, a reader's question goes to the answers with acts: the lower route again
   * under `/process`, answering `{answer, acts}`.
   */
  override protected def ask(path: String): Answer =
    val withActs = w.notes.contains("golden") &&
      (path.startsWith("/answers/") || path.startsWith("/markets/"))
    super.ask(if withActs then s"/process$path" else path)

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

  /** The digest ankka gives the appraiser at an instruction set. */
  private def digestOf(set: String): String =
    Appraiser
      .blueprint(Instructions.named(set).getOrElse(fail(s"no instruction set $set")))
      .digest

  /**
   * The number ankka gave the latest version of the appraiser carrying the instruction set `v<n>`.
   * ankka numbers every registration that changes the blueprint, so once a scenario has registered
   * `v2` the service carries `v1` again only at a third number; a feature's "blueprint version 1"
   * is the version with the first instruction set, "version 2" the one with the second.
   */
  protected def versionAt(n: Int): Int =
    val digest = digestOf(s"v$n")
    agents.blueprints
      .versions(Blueprint)
      .filter(_.digest == digest)
      .map(_.number)
      .maxOption
      .getOrElse(fail(s"the appraiser was never registered with v$n"))

  Given(
    "the appraiser, registered at blueprint version 1, whose worker {string} speaks for the holder {string}"
  ) { (worker: String, holder: String) =>
    val versions = agents.blueprints.versions(Blueprint)
    assert(versions.exists(_.number == 1), versions.toString)
    // A scenario before this one may have left the second instruction set current.
    if versions.last.digest != digestOf(Instructions.V1) then
      ok(http.post("/process/appraiser/versions", Obj("instructions" -> Instructions.V1))): Unit
    val current = versionAt(1)
    assert(
      agents.blueprints.version(Blueprint, current).workers.exists(_.name == worker),
      s"no worker $worker"
    )
    val record = held(s"/holders/$holder")
    assert(record("writers").arr.exists(_.str == Writer), record.render())
    script.instructions = Instructions.V1
    w.published :+= "blueprint-version" -> Ids.version(Blueprint, current)
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
    assertEquals(act("version").num.toInt, versionAt(version))
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
  ) { (n: Int) =>
    val version    = versionAt(n)
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
  ) { (id: String, n: Int) =>
    val version = versionAt(n)
    val run     = runId(id)
    val held    = this.held(s"/process/runs/$run")("run")
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
  ) { (id: String, n: Int) =>
    val act = actOf("revision", w.revision).getOrElse(fail("the revision has no act"))
    assertEquals(w.notes("act"), act("id").str)
    assertEquals(w.notes("walkedRun"), s"run:${runId(id)}")
    assertEquals(w.notes("version"), s"blueprint-version:${Ids.version(Blueprint, versionAt(n))}")
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

  // ── The golden set ────────────────────────────────────────────────────────

  /**
   * The day a tranche is read: the last of its month, after every document in it was published, so
   * a run's records are dated in the month and no excerpt is observed before it was published.
   */
  private def trancheDay(tranche: String): Moment =
    Moment.parse(if tranche == "june" then "2026-06-30T09:00:00Z" else "2026-05-31T09:00:00Z").get

  private def docs(tranche: String): Vector[Document] =
    goldenSet.tranche(tranche).map(_.documents).getOrElse(fail(s"no tranche $tranche"))

  private def goldenRun(holder: String, tranche: String): String =
    w.notes.getOrElse(
      s"run:$holder:$tranche",
      fail(s"the appraiser was not run for $holder over the $tranche tranche")
    )

  private def revisionOf(holder: String, tranche: String): String =
    s"${goldenRun(holder, tranche)}.yes"

  private def evidenceOf(tranche: String): Set[String] =
    docs(tranche).map(d => evidenceId(tranche, d.id)).toSet

  private def claimsOf(holder: String, tranche: String): Set[String] =
    docs(tranche).map(d => claimId(goldenRun(holder, tranche), tranche, d.id)).toSet

  /** The tranche a run of the holder wrote a claim in, from the claim's id. */
  private def trancheOf(holder: String, claim: String): String =
    Vector("may", "june")
      .find(t => w.notes.get(s"run:$holder:$t").exists(run => claim.startsWith(s"$run.")))
      .getOrElse(fail(s"no run of $holder wrote the claim $claim"))

  private def lineOf(holder: String, hypothesis: String): Vector[ujson.Value] =
    held(s"/beliefs/line?holder=$holder&hypothesis=${w.hypotheses(hypothesis)}")(
      "revisions"
    ).arr.toVector
      .sortBy(_("n").num)

  private def restsOf(revision: ujson.Value): Map[String, Option[Double]] =
    revision("restsOn").arr.map(r => r("claim").str -> r.obj.get("weight").map(_.num)).toMap

  private def answer: ujson.Value = answered("answer")

  /** The provenance the answer gives a record, or a failure naming it. */
  private def actIn(kind: String, id: String): ujson.Value =
    answered("acts").arr
      .find(e => e("record")("kind").str == kind && e("record")("id").str == id)
      .getOrElse(fail(s"the answer gives no act for $kind $id: ${w.last.body}"))

  /**
   * Runs the appraiser for the holder over the tranche on that tranche's day, waits for the run to
   * end, and notes everything a completed run wrote for the graph to catch up with.
   */
  protected def runTranche(
      holder: String,
      tranche: String,
      follows: Option[String] = None
  ): String =
    clock.set(trancheDay(tranche))
    val id = runId(s"$holder-$tranche")
    ok(write("/process/runs", runBody(tranche, holder, id, follows = follows)))
    val ended = awaitEnded(id)
    w.notes(s"run:$holder:$tranche") = id
    w.notes("run") = id
    w.holder = holder
    if ended("ending")("status").str == "completed" then
      // Evidence an earlier run of this scenario recorded from the tranche keeps that run's act,
      // so a second holder's run writes the claims and the revision alone.
      val n     = docs(tranche).size
      val first = !w.notes.contains(s"read:$tranche")
      val expected = Map("claim" -> n, "revision" -> 1) ++
        (if first then Map("evidence" -> n) else Map.empty)
      w.notes(s"read:$tranche") = id
      // The rows the acts are listed from may lag the writes by a moment.
      val held = Eventually.eventually(60.seconds) {
        val all   = acts(id)
        val kinds = all.groupBy(_("record")("kind").str).map((k, v) => k -> v.size)
        assertEquals(kinds, expected, all.map(_("record")).mkString(", "))
        all
      }
      w.published ++= Vector("run" -> id, "run-ending" -> id) ++
        held.map(a => "act" -> a("id").str) ++
        held.map(a => a("record")("kind").str -> a("record")("id").str)
    id

  private def bothTranches(holder: String): Unit =
    runTranche(holder, "may"): Unit
    runTranche(holder, "june", Some(revisionOf(holder, "may"))): Unit

  Given("the golden set") { () =>
    w.notes("golden") = "yes"
    val question = goldenSet.question
    w.question = s"${w.p}-${question.id}"
    ok(
      write(
        "/questions",
        Obj(
          "id"        -> w.question,
          "statement" -> question.statement,
          "hypotheses" -> Arr.from(
            question.hypotheses.map(h => Obj("id" -> h.id, "statement" -> h.statement))
          ),
          "dated" -> World.date("1 May")
        )
      )
    ): Unit
    question.hypotheses.foreach(h => w.hypotheses(h.statement) = s"${w.question}/${h.id}")
    w.hypothesis = s"${w.question}/yes"
    w.published :+= "question" -> w.question
    // The sources and the holders the service registered as it started.
    question.sources.foreach(source =>
      assertEquals(held(s"/sources/${source.id}")("name").str, source.name)
    )
    Vector(HolderA, HolderB).foreach(holder =>
      assertEquals(held(s"/holders/$holder")("kind").str, "agent")
    )
  }

  Given("a scripted model that answers from the expected readings")(() => script.install())

  // ── The documents ─────────────────────────────────────────────────────────

  When("a reader reads the golden set")(() => w.notes("read") = goldenSet.question.id)

  Then(
    "the May tranche holds five documents: {string}, {string}, {string}, {string} and {string}"
  ) { (a: String, b: String, c: String, d: String, e: String) =>
    assertEquals(docs("may").map(_.kind).toList, List(a, b, c, d, e))
  }

  Then("the June tranche holds three documents: {string}, {string} and {string}") {
    (a: String, b: String, c: String) =>
      assertEquals(docs("june").map(_.kind).toList, List(a, b, c))
  }

  Then(
    "each document has a source, a locator, the time it was published, its text and an expected reading naming a hypothesis, a stance and a statement"
  ) { () =>
    val hypotheses = goldenSet.question.hypotheses.map(h => s"${goldenSet.question.id}/${h.id}")
    (docs("may") ++ docs("june")).foreach { doc =>
      assert(Sources.values.toSet.contains(doc.source), s"${doc.id}: no source ${doc.source}")
      assert(doc.locator.nonEmpty, doc.id)
      assert(doc.publishedAt.millis > 0, doc.id)
      assert(doc.text.split("\\s+").length >= 100, s"${doc.id}: ${doc.text.length} characters")
      assert(hypotheses.contains(doc.expectedReading.hypothesis), doc.expectedReading.hypothesis)
      assert(Set("supports", "contradicts").contains(doc.expectedReading.stance), doc.id)
      assert(doc.expectedReading.statement.nonEmpty, doc.id)
    }
  }

  // ── The runs ──────────────────────────────────────────────────────────────

  When("the appraiser is run for {string} over the May tranche") { (holder: String) =>
    runTranche(holder, "may"): Unit
  }

  Given("the appraiser run for {string} over the May tranche") { (holder: String) =>
    runTranche(holder, "may"): Unit
  }

  When(
    "the appraiser is run for {string} over the June tranche, following the first belief revision"
  ) { (holder: String) =>
    runTranche(holder, "june", Some(revisionOf(holder, "may"))): Unit
  }

  Given("the appraiser run for {string} over the May tranche and then the June tranche") {
    (holder: String) => bothTranches(holder)
  }

  When("the appraiser is run for {string} over the May tranche and then the June tranche") {
    (holder: String) => bothTranches(holder)
  }

  Given(
    "the appraiser run for {string} and, at blueprint version {int}, for {string}, each over both tranches"
  ) { (first: String, version: Int, second: String) =>
    bothTranches(first)
    registerAgain(version)
    bothTranches(second)
  }

  Then("the run has the run status {string}") { (status: String) =>
    val end = ending(w.notes("run"))
    assertEquals(end("status").str, status, end.render())
  }

  Then("both runs have the run status {string}") { (status: String) =>
    Vector("may", "june").foreach { tranche =>
      val end = ending(goldenRun(w.holder, tranche))
      assertEquals(end("status").str, status, end.render())
    }
  }

  Then("five pieces of evidence and five claims are held, each with an act naming the run") { () =>
    val run = w.notes("run")
    // The rows the acts are listed from may lag the writes by a moment.
    Eventually.eventually(60.seconds) {
      assertEquals(actsOf(run, "evidence").size, 5)
      assertEquals(actsOf(run, "claim").size, 5)
    }
    docs("may").foreach { doc =>
      val evidence = evidenceId("may", doc.id)
      val claim    = claimId(run, "may", doc.id)
      assertEquals(held(s"/evidence/$evidence")("id").str, evidence)
      assertEquals(held(s"/claims/$claim")("claim")("id").str, claim)
      assertEquals(actOf("evidence", evidence).map(_("run").str), Some(run), doc.id)
      assertEquals(actOf("claim", claim).map(_("run").str), Some(run), doc.id)
    }
  }

  Then(
    "each claim derives from the evidence recorded from one document, with the stance that document's expected reading names"
  ) { () =>
    val run = w.notes("run")
    docs("may").foreach { doc =>
      val claim = held(s"/claims/${claimId(run, "may", doc.id)}")("claim")
      assertEquals(claim("derivesFrom").arr.map(_.str).toList, List(evidenceId("may", doc.id)))
      val stances = claim("stances").arr.map(s => s"${s("hypothesis").str} ${s("stance").str}")
      assertEquals(
        stances.toList,
        List(s"${w.question}/${doc.expectedReading.hypothesisId} ${doc.expectedReading.stance}")
      )
    }
  }

  Then(
    "the critique of the run passed, every claim deriving from evidence recorded from a document of the May tranche"
  ) { () =>
    val run = w.notes("run")
    val end = ending(run)
    assertEquals(end("status").str, "completed", end.render())
    assert(!end.obj.contains("reason"), end.render())
    docs("may").foreach { doc =>
      val evidence = held(s"/evidence/${evidenceId("may", doc.id)}")
      assertEquals(evidence("source").str, doc.source)
      assertEquals(evidence("locator").str, doc.locator)
    }
  }

  Then(
    "the belief of {string} in {string} has one belief revision, with a probability below {string}, resting on the five claims"
  ) { (holder: String, hypothesis: String, bound: String) =>
    val revisions = lineOf(holder, hypothesis)
    assertEquals(revisions.map(_("id").str), Vector(revisionOf(holder, "may")))
    assert(revisions.head("probability").num < bound.toDouble, revisions.head.render())
    assertEquals(restsOf(revisions.head).keySet, claimsOf(holder, "may"))
  }

  Then(
    "the belief of {string} in {string} has two belief revisions, the second following the first"
  ) { (holder: String, hypothesis: String) =>
    val revisions = lineOf(holder, hypothesis)
    assertEquals(
      revisions.map(_("id").str),
      Vector(revisionOf(holder, "may"), revisionOf(holder, "june"))
    )
    assertEquals(revisions(1)("follows").str, revisions(0)("id").str)
  }

  Then(
    "the current belief revision has a probability above {string} and rests on claims deriving from the June tranche's documents"
  ) { (bound: String) =>
    val current = belief()("current")
    assertEquals(current("id").str, revisionOf(w.holder, "june"))
    assert(current("probability").num > bound.toDouble, current.render())
    assertEquals(restsOf(current).keySet, claimsOf(w.holder, "june"))
    claimsOf(w.holder, "june").foreach { claim =>
      val derives = held(s"/claims/$claim")("claim")("derivesFrom").arr.map(_.str).toSet
      assert(derives.subsetOf(evidenceOf("june")), s"$claim derives from $derives")
    }
  }

  // ── A critique that fails ─────────────────────────────────────────────────

  Given(
    "a scripted model that states one claim deriving from evidence a writer recorded from no document"
  ) { () =>
    script.install()
    val stray = ok(
      http.post(
        "/evidence",
        Obj(
          "source"     -> Sources("the rumour mill"),
          "locator"    -> s"https://rumours.example/${w.p}/stray",
          "excerpt"    -> "a passage no document of the golden set holds",
          "observedAt" -> World.date("10 May")
        )
      )
    ).json("id").str
    w.evidence = stray
    w.notes("stray") = "filing"
    script.withClaimFrom(stray, w.notes("stray"))
    script.judgeNo()
  }

  Then("the run has the run status {string}, naming the claim") { (status: String) =>
    val run = w.notes("run")
    val end = ending(run)
    assertEquals(end("status").str, status, end.render())
    w.claim = claimId(run, "may", w.notes("stray"))
    assert(end("reason").str.contains(w.claim), end("reason").str)
  }

  Then("the claims the run stated are held, each with its act") { () =>
    val run = w.notes("run")
    docs("may").foreach { doc =>
      val claim = claimId(run, "may", doc.id)
      val held  = this.held(s"/claims/$claim")("claim")
      val from  = if doc.id == w.notes("stray") then w.evidence else evidenceId("may", doc.id)
      assertEquals(held("derivesFrom").arr.map(_.str).toList, List(from))
      val act = Eventually.eventually()(actOf("claim", claim).getOrElse(fail(s"no act for $claim")))
      assertEquals(act("run").str, run)
    }
  }

  Then("the holder {string} has no belief") { (holder: String) =>
    val head = held(s"/beliefs/current?holder=$holder&hypothesis=${w.hypothesis}")
    assertEquals(head("count").num.toInt, 0, head.render())
    assert(head.obj.get("current").forall(_.isNull), head.render())
    assertEquals(actsOf(w.notes("run"), "revision"), Vector.empty)
  }

  // ── A second version ──────────────────────────────────────────────────────

  /** Registers the appraiser again with the second instruction set, at the version `n` names. */
  private def registerAgain(n: Int): Int =
    val answer = http.post("/process/appraiser/versions", Obj("instructions" -> Instructions.V2))
    assertEquals(answer.status, 201, answer.body)
    assertEquals(answer.json("blueprint").str, Blueprint)
    val version = answer.json("version").num.toInt
    assertEquals(version, versionAt(n))
    // Sent again, the same instructions are the version held.
    val again = http.post("/process/appraiser/versions", Obj("instructions" -> Instructions.V2))
    assertEquals(again.status, 200, again.body)
    assertEquals(again.json("version").num.toInt, version)
    val listed = held("/process/appraiser/versions")("versions").arr
    assertEquals(listed.last("version").num.toInt, version)
    assertEquals(listed.last("digest").str, digestOf(Instructions.V2))
    // The service holds the version a moment after ankka registers it.
    val detail = Eventually.eventually(30.seconds)(held(s"/process/appraiser/versions/$version"))
    assertEquals(detail("id").str, Ids.version(Blueprint, version))
    assertEquals(detail("blueprint")("name").str, Blueprint)
    assertEquals(detail("digest").str, digestOf(Instructions.V2))
    script.instructions = Instructions.V2
    w.published :+= "blueprint-version" -> Ids.version(Blueprint, version)
    version

  Given(
    "the appraiser registered again with different instructions, at blueprint version {int}, whose worker {string} speaks for the holder {string}"
  ) { (n: Int, worker: String, holder: String) =>
    val version = registerAgain(n)
    assert(
      agents.blueprints.version(Blueprint, version).workers.exists(_.name == worker),
      s"no worker $worker"
    )
    val record = held(s"/holders/$holder")
    assert(record("writers").arr.exists(_.str == Writer), record.render())
  }

  Then("the belief of {string} in {string} has two belief revisions") {
    (holder: String, hypothesis: String) =>
      assertEquals(
        lineOf(holder, hypothesis).map(_("id").str),
        Vector(revisionOf(holder, "may"), revisionOf(holder, "june"))
      )
      w.holder = holder
  }

  private def revisionsNameVersion(holder: String, n: Int): Unit =
    Vector("may", "june").foreach { tranche =>
      val revision = revisionOf(holder, tranche)
      val act = Eventually.eventually()(
        actOf("revision", revision).getOrElse(fail(s"no act for $revision"))
      )
      assertEquals(act("version").num.toInt, versionAt(n), s"$holder, $tranche")
    }

  Then("each of its belief revisions has an act naming blueprint version {int}") { (n: Int) =>
    revisionsNameVersion(w.holder, n)
  }

  Then("each belief revision of {string} has an act naming blueprint version {int}") {
    (holder: String, n: Int) => revisionsNameVersion(holder, n)
  }

  // ── The answers with acts ─────────────────────────────────────────────────

  Then(
    "the case holds every claim that supports the hypothesis, from the May tranche and from the June tranche"
  ) { () =>
    val holder = w.holder
    val supporting = Vector("may", "june").flatMap { tranche =>
      docs(tranche)
        .filter(_.expectedReading.stance == "supports")
        .map(doc => claimId(goldenRun(holder, tranche), tranche, doc.id))
    }.toSet
    assert(supporting.exists(_.startsWith(goldenRun(holder, "may"))), "no May claim supports")
    assertEquals(claimsIn(answer("claims")).toSet, supporting)
    assertEquals(answer("revisedClaims").arr.size, 0)
  }

  Then("each claim is given with the act and the run that wrote it") { () =>
    val holder = w.holder
    answer("claims").arr.foreach { support =>
      val claim = support("claim")("id").str
      val entry = actIn("claim", claim)
      assertEquals(entry("run")("id").str, goldenRun(holder, trancheOf(holder, claim)))
      assertEquals(entry("act")("step").str, "read")
      assertEquals(entry("act")("worker").str, "reader")
      assertEquals(entry("runEnding")("status").str, "completed")
    }
  }

  Then("the reader is given the first belief revision, which the run over the May tranche wrote") {
    () =>
      val revision = answer("revision")
      val holder   = revision("holder").str
      assertEquals(revision("id").str, revisionOf(holder, "may"))
      val entry = actIn("revision", revision("id").str)
      assertEquals(entry("run")("id").str, goldenRun(holder, "may"))
      assertEquals(entry("act")("step").str, "revise")
  }

  Then(
    "what was learned about the question after {string} is the evidence, the claims and the belief revision the run over the June tranche wrote, and nothing else"
  ) { (day: String) =>
    val holder = w.holder
    ask(s"/answers/learned?question=${w.question}&after=${World.noon(day)}"): Unit
    val learned = answer
    assertEquals(learned("evidence").arr.map(_("id").str).toSet, evidenceOf("june"))
    assertEquals(learned("claims").arr.map(_("id").str).toSet, claimsOf(holder, "june"))
    assertEquals(learned("revisions").arr.map(_("id").str).toList, List(revisionOf(holder, "june")))
    val run = goldenRun(holder, "june")
    evidenceOf("june").foreach(id => assertEquals(actIn("evidence", id)("run")("id").str, run))
    claimsOf(holder, "june").foreach(id => assertEquals(actIn("claim", id)("run")("id").str, run))
    assertEquals(actIn("revision", revisionOf(holder, "june"))("run")("id").str, run)
  }

  Then(
    "the explanation gives the claim derived from the approval document as newly rested on, with its evidence and its source"
  ) { () =>
    val holder = answer("to")("holder").str
    assertEquals(answer("from")("id").str, revisionOf(holder, "may"))
    assertEquals(answer("to")("id").str, revisionOf(holder, "june"))
    val doc      = document("june", "approval")
    val approval = claimId(goldenRun(holder, "june"), "june", "approval")
    val newly = answer("newlyRestedOn").arr
      .find(_("claim")("id").str == approval)
      .getOrElse(fail(s"the approval's claim is not newly rested on: ${w.last.body}"))
    assertEquals(newly("claim")("statement").str, doc.expectedReading.statement)
    assertEquals(
      newly("evidence").arr.map(_("id").str).toList,
      List(evidenceId("june", "approval"))
    )
    assertEquals(newly("evidence").arr.head("source")("id").str, doc.source)
    w.claim = approval
    w.holder = holder
  }

  Then("the claim is given with the act and the run over the June tranche that wrote it") { () =>
    val entry = actIn("claim", w.claim)
    assertEquals(entry("run")("id").str, goldenRun(w.holder, "june"))
    assertEquals(entry("act")("step").str, "read")
    assertEquals(entry("act")("worker").str, "reader")
  }

  /** The claims one side of the comparison rests on, each with the evidence it derives from. */
  private def side(name: String): Vector[(String, Set[String], Option[Double])] =
    answer(name).arr.toVector.map { support =>
      (
        support("claim")("id").str,
        support("evidence").arr.map(_("id").str).toSet,
        support.obj.get("weight").map(_.num)
      )
    }

  Then("no claim is both holders', each resting on claims of its own") { () =>
    val a = answer("a")("holder").str
    val b = answer("b")("holder").str
    assertEquals(answer("both").arr.size, 0, w.last.body)
    assertEquals(side("onlyA").map(_._1).toSet, claimsOf(a, "june"))
    assertEquals(side("onlyB").map(_._1).toSet, claimsOf(b, "june"))
  }

  Then(
    "every claim one holder rests on derives from the same evidence as a claim the other rests on"
  ) { () =>
    val ofA = side("onlyA").map(_._2)
    val ofB = side("onlyB").map(_._2)
    assert(ofA.nonEmpty, w.last.body)
    assertEquals(ofA.toSet, ofB.toSet)
    assertEquals(ofA.toSet, evidenceOf("june").map(Set(_)))
  }

  Then("at least one piece of evidence is read by the two holders with different weights") { () =>
    val ofA = side("onlyA").map(s => s._2 -> s._3).toMap
    val ofB = side("onlyB").map(s => s._2 -> s._3).toMap
    assert(ofA.keySet.exists(e => ofA(e) != ofB(e)), s"$ofA, $ofB")
  }

  Given(
    "a market about the question with the outcomes {string} and {string}, resolved to the outcome {string} on the evidence recorded from the approval document"
  ) { (first: String, second: String, outcome: String) =>
    w.market = w.fresh("m")
    ok(
      write(
        "/markets",
        marketBody(
          w.market,
          "Example Exchange",
          Seq(first -> s"${w.question}/yes", second -> s"${w.question}/no"),
          w.question
        )
      )
    ): Unit
    // Resolved afterwards: the clock moves to the day of the resolution first. The resolution is
    // published with the market, whose version moves, so the wait is on the market.
    clock.set(Moment.parse(World.date("14 November")).get)
    ok(resolve(Some(outcome), Seq(evidenceId("june", "approval")))): Unit
    w.published :+= "market" -> w.market
  }

  Then(
    "the reader is given the last belief revision of {string}, with an act naming blueprint version {int}, and of {string}, with an act naming blueprint version {int}"
  ) { (a: String, versionA: Int, b: String, versionB: Int) =>
    val beliefs = answer("beliefs").arr.map(x => x("holder").str -> x("revision")("id").str).toMap
    assertEquals(beliefs, Map(a -> revisionOf(a, "june"), b -> revisionOf(b, "june")))
    answer("beliefs").arr.foreach(x => assertEquals(x("resolvedTo").bool, true, x.render()))
    Vector(a -> versionA, b -> versionB).foreach { (holder, n) =>
      val entry = actIn("revision", revisionOf(holder, "june"))
      assertEquals(entry("blueprintVersion")("version").num.toInt, versionAt(n), holder)
      assertEquals(entry("run")("id").str, goldenRun(holder, "june"))
    }
  }

  // ── No key, no network ────────────────────────────────────────────────────

  Given("no model key and no network") { () =>
    Vector(Providers.ModelKey, Providers.JudgmentKey).foreach { key =>
      assert(sys.env.get(key).forall(_.trim.isEmpty), s"$key is set in the test JVM")
    }
    // The fixtures' providers are the scripted ones; nothing a run asks leaves the JVM.
    assertEquals(ServiceFixture.model.name, "test")
    assertEquals(ServiceFixture.judge.name, "test")
  }
