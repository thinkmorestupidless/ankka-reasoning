package reasoning.process.answers

import reasoning.belief.answers.*
import reasoning.belief.domain.{Moment, Rules}
import reasoning.graph.GraphReader
import reasoning.market.answers.{BeliefsAtResolution, MarketAnswers, WhyResolved}
import reasoning.process.ProcessVocabulary as V
import reasoning.process.domain.RecordRef

/**
 * The lower layers' answers again, each with the acts of the records in it: the lower answer runs
 * unchanged, and one walk from the records it names reaches their acts, runs, endings and versions.
 * A reader asks once.
 */
final class ProcessAnswers(graph: GraphReader, beliefs: BeliefAnswers, markets: MarketAnswers):

  import ProcessAnswers.*

  // ── The walk ──────────────────────────────────────────────────────────────

  /**
   * The lower answer and, for every record in it that an act wrote, the act with its run, the run's
   * ending if it has one, and the version the run was of.
   */
  def withActs[A](lower: => A, records: A => Seq[RecordRef]): WithActs[A] =
    val answer = lower
    WithActs(answer, actsOf(records(answer)))

  private def actsOf(records: Seq[RecordRef]): Vector[ActOf] =
    val ids = records.distinct.map(nodeId)
    if ids.isEmpty then Vector.empty
    else
      graph
        .read(
          "MATCH (a:Act)-[:WROTE]->(r:Element) WHERE r.id IN $records " +
            "MATCH (a)-[:IN]->(run:Run)-[:OF]->(v:BlueprintVersion) " +
            "OPTIONAL MATCH (e:RunEnding)-[:ENDED]->(run) " +
            "RETURN r.id AS record, properties(a) AS act, properties(run) AS run, properties(e) AS ending, properties(v) AS version",
          Map("records" -> ids)
        ) { row =>
          ActOf(
            recordRef(row.string("record")),
            actView(row.properties("act")),
            runView(row.properties("run")),
            row.propertiesOption("ending").map(endingView),
            versionView(row.properties("version"))
          )
        }
        .sortBy(a => (a.record.kind, a.record.id))

  // ── The answers ───────────────────────────────────────────────────────────

  def belief(holder: String, hypothesis: String, at: At = At.now): WithActs[BeliefAnswer] =
    withActs(beliefs.belief(holder, hypothesis, at), Records.belief)

  def beliefChange(first: String, second: String, at: At = At.now): WithActs[BeliefChange] =
    withActs(beliefs.beliefChange(first, second, at), Records.beliefChange)

  def caseFor(hypothesis: String, stance: String, at: At = At.now): WithActs[CaseAnswer] =
    withActs(beliefs.caseFor(hypothesis, stance, at), Records.caseFor)

  def learned(question: String, after: Moment, at: At = At.now): WithActs[Learned] =
    withActs(beliefs.learned(question, after, at), Records.learned)

  def comparison(hypothesis: String, a: String, b: String, at: At = At.now): WithActs[Comparison] =
    withActs(beliefs.comparison(hypothesis, a, b, at), Records.comparison)

  def restingOnRevised(question: String, at: At = At.now): WithActs[RestingOnRevised] =
    withActs(beliefs.restingOnRevised(question, at), Records.restingOnRevised)

  def whyResolved(market: String, at: At = At.now): WithActs[WhyResolved] =
    withActs(markets.whyResolved(market, at), Records.whyResolved)

  def beliefsAtResolution(market: String, at: At = At.now): WithActs[BeliefsAtResolution] =
    withActs(markets.beliefsAtResolution(market, at), Records.beliefsAtResolution)

  /**
   * Everything a run wrote: the run, its ending once it has one, and each act with the record it
   * names, those dated and recorded by the time asked about. Refused when the graph holds no such
   * run.
   */
  def run(runId: String, at: At = At.now): RunWrote =
    val found = graph
      .read(
        "MATCH (run:Run {id: $run})-[:OF]->(v:BlueprintVersion) " +
          "OPTIONAL MATCH (e:RunEnding)-[:ENDED]->(run) " +
          "RETURN properties(run) AS run, properties(e) AS ending, properties(v) AS version",
        Map("run" -> V.Run.id(runId))
      )(row => (runView(row.properties("run")), row.propertiesOption("ending").map(endingView)))
      .headOption
      .getOrElse(throw Rules.notHeld("run", runId))
    val wrote = graph
      .read(
        "MATCH (a:Act)-[:IN]->(:Run {id: $run}) WHERE a.dated <= $asOf AND a.recordedAt <= $by " +
          "MATCH (a)-[:WROTE]->(x:Element) RETURN properties(a) AS act, x.id AS record",
        at.parameters + ("run" -> V.Run.id(runId))
      )(row => Wrote(actView(row.properties("act")), recordRef(row.string("record"))))
      .sortBy(w => (w.record.kind, w.record.id))
    RunWrote(found._1, found._2, wrote)

object ProcessAnswers:

  import BeliefAnswers.{local, moment, text}

  /** A record's node id, by its kind. */
  def nodeId(record: RecordRef): String = s"${record.kind}:${record.id}"

  /** A node id as a record: `claim:c1` is the claim `c1`. */
  def recordRef(nodeId: String): RecordRef = RecordRef(nodeId.takeWhile(_ != ':'), local(nodeId))

  private def at(properties: Map[String, Any], name: String): Moment =
    moment(properties, name).getOrElse(Moment(0))

  def actView(p: Map[String, Any]): ActView =
    ActView(
      p("id").toString,
      text(p, "step").getOrElse(""),
      text(p, "worker").getOrElse(""),
      text(p, "recordKind").getOrElse(""),
      at(p, "dated"),
      at(p, "recordedAt")
    )

  def runView(p: Map[String, Any]): RunView =
    RunView(
      local(p("id").toString),
      text(p, "blueprint").getOrElse(""),
      p.get("version").map(_.asInstanceOf[Number].intValue).getOrElse(0),
      text(p, "startedBy").getOrElse(""),
      at(p, "dated"),
      at(p, "recordedAt")
    )

  def endingView(p: Map[String, Any]): RunEndingView =
    RunEndingView(
      local(p("id").toString),
      text(p, "status").getOrElse(""),
      text(p, "reason"),
      at(p, "dated"),
      at(p, "recordedAt")
    )

  def versionView(p: Map[String, Any]): VersionView =
    VersionView(
      local(p("id").toString),
      text(p, "blueprint").getOrElse(""),
      p.get("version").map(_.asInstanceOf[Number].intValue).getOrElse(0),
      text(p, "digest").getOrElse(""),
      at(p, "dated"),
      at(p, "recordedAt")
    )

  /** The records each lower answer names: evidence, claims and belief revisions. */
  object Records:

    private def evidence(views: Iterable[EvidenceView]): Vector[RecordRef] =
      views.map(e => RecordRef(RecordRef.Evidence, e.id)).toVector

    private def claim(view: ClaimView): RecordRef = RecordRef(RecordRef.Claim, view.id)

    private def revision(view: RevisionView): RecordRef = RecordRef(RecordRef.Revision, view.id)

    private def supports(all: Iterable[Support]): Vector[RecordRef] =
      all.toVector.flatMap(s => claim(s.claim) +: evidence(s.evidence))

    def belief(a: BeliefAnswer): Vector[RecordRef] =
      a.revision.map(revision).toVector ++ supports(a.restsOn)

    def beliefChange(a: BeliefChange): Vector[RecordRef] =
      Vector(revision(a.from), revision(a.to)) ++
        supports(a.newlyRestedOn) ++ supports(a.noLongerRestedOn) ++
        supports(a.stillRestedOn.map(_.support)) ++ evidence(a.observedBetween)

    def caseFor(a: CaseAnswer): Vector[RecordRef] =
      supports(a.claims) ++ supports(a.revisedClaims)

    def learned(a: Learned): Vector[RecordRef] =
      evidence(a.evidence) ++ a.claims.map(claim) ++ a.revisions.map(revision)

    def comparison(a: Comparison): Vector[RecordRef] =
      a.a.revision.map(revision).toVector ++ a.b.revision.map(revision).toVector ++
        supports(a.both.map(_.support)) ++ supports(a.onlyA) ++ supports(a.onlyB)

    def restingOnRevised(a: RestingOnRevised): Vector[RecordRef] =
      a.beliefs.flatMap(b => revision(b.revision) +: supports(b.revisedClaims))

    def whyResolved(a: WhyResolved): Vector[RecordRef] = evidence(a.evidence)

    def beliefsAtResolution(a: BeliefsAtResolution): Vector[RecordRef] =
      a.beliefs.map(b => revision(b.revision))
