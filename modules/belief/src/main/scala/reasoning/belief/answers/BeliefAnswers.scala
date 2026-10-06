package reasoning.belief.answers

import reasoning.belief.domain.{Moment, Refused, Rules}
import reasoning.graph.{GraphReader, Row}

/**
 * The time an answer is asked for: only records dated at or before `asOf`, and recorded at or
 * before `by`, count. Left out, each is the end of time.
 */
final case class At(asOf: Long = Long.MaxValue, by: Long = Long.MaxValue):
  def parameters: Map[String, Any] = Map("asOf" -> asOf, "by" -> by)

object At:
  val now: At = At()

  def of(asOf: Option[Moment], asRecordedBy: Option[Moment]): At =
    At(asOf.fold(Long.MaxValue)(_.millis), asRecordedBy.fold(Long.MaxValue)(_.millis))

/**
 * The belief layer's answers, each a walk of the graph that starts from a node found by its id.
 *
 * A record counts at a time when it is dated and recorded no later. A claim is a revised claim when
 * a claim that revises it counts. A belief's revision at a time is the last in its line that
 * counts, which is well defined because a revision is neither dated nor recorded before the one it
 * follows.
 */
final class BeliefAnswers(graph: GraphReader):

  import BeliefAnswers.*

  // ── Queries ───────────────────────────────────────────────────────────────

  private val CurrentThen =
    "NOT EXISTS { MATCH (n:BeliefRevision)-[:FOLLOWS]->(r) WHERE n.dated <= $asOf AND n.recordedAt <= $by }"

  private def revisionById(id: String): Option[Stated] =
    graph
      .read(
        s"MATCH (r:Element {id: $$id}) WHERE r:BeliefRevision RETURN $RevisionColumns",
        Map("id" -> s"revision:$id")
      )(stated)
      .headOption

  /**
   * A revision named by its id counts at a time like any other record, or the answer is refused.
   */
  private def countedThen(stated: Stated, at: At): Stated =
    if stated.view.dated.millis <= at.asOf && stated.view.recordedAt.millis <= at.by then stated
    else
      throw Refused(
        "answer.revision.later-than-asked",
        422,
        "The revision is dated or was recorded later than the time asked about.",
        "revision"   -> stated.view.id,
        "dated"      -> stated.view.dated.iso,
        "recordedAt" -> stated.view.recordedAt.iso
      )

  private def revisionThen(holder: String, hypothesis: String, at: At): Option[Stated] =
    graph
      .read(
        "MATCH (y:Element {id: $hypothesis})<-[:BELIEF_IN]-(r:BeliefRevision)-[:HELD_BY]->(:Element {id: $holder}) " +
          "WHERE r.dated <= $asOf AND r.recordedAt <= $by " +
          s"WITH r ORDER BY r.n DESC LIMIT 1 RETURN $RevisionColumns",
        at.parameters ++ Map(
          "hypothesis" -> s"hypothesis:$hypothesis",
          "holder"     -> s"holder:$holder"
        )
      )(stated)
      .headOption

  /**
   * Each claim as a support: its evidence and sources, what revises it, and who rests on it now.
   */
  private def supports(claims: Seq[String], at: At): Map[String, Support] =
    if claims.isEmpty then Map.empty
    else
      graph
        .read(
          "MATCH (c:Element) WHERE c.id IN $claims AND c:Claim " +
            "RETURN properties(c) AS claim, [(c)-[:STATED_BY]->(h) | h.id][0] AS holder, " +
            "[(c)-[:DERIVES_FROM]->(e)-[:FROM_SOURCE]->(s) | {evidence: properties(e), source: properties(s)}] AS evidence, " +
            "[(later:Claim)-[:REVISES]->(c) WHERE later.dated <= $asOf AND later.recordedAt <= $by | later.id] AS revisedBy, " +
            "[(r:BeliefRevision)-[:RESTS_ON]->(c) WHERE r.dated <= $asOf AND r.recordedAt <= $by AND " + CurrentThen +
            " | [(r)-[:HELD_BY]->(x) | x.id][0]] AS heldBy",
          at.parameters + ("claims" -> claims.map(id => s"claim:$id"))
        ) { row =>
          val claim = claimView(row.properties("claim"), row.stringOption("holder"))
          val evidence = row.propertiesList("evidence").map { both =>
            evidenceView(
              map(both("evidence")),
              Option(both("source")).map(map).filter(_.contains("name"))
            )
          }
          claim.id -> Support(
            claim,
            None,
            evidence.sortBy(e => (e.dated.millis, e.id)),
            row.strings("revisedBy").map(local).sorted,
            row.strings("heldBy").map(local).distinct.sorted
          )
        }
        .toMap

  // ── Answers ───────────────────────────────────────────────────────────────

  /** A belief at a time: the revision that was current then and what it rested on. */
  def belief(holder: String, hypothesis: String, at: At = At.now): BeliefAnswer =
    revisionThen(holder, hypothesis, at) match
      case None => BeliefAnswer(None, Vector.empty)
      case Some(stated) =>
        val held = supports(stated.restsOn.map(_._1), at)
        BeliefAnswer(
          Some(stated.view),
          stated.restsOn.flatMap((claim, weight) => held.get(claim).map(_.copy(weight = weight)))
        )

  /** Why a belief changed between two of its revisions, in whichever order they are named. */
  def beliefChange(first: String, second: String, at: At = At.now): BeliefChange =
    val one = countedThen(revisionById(first).getOrElse(throw Rules.notHeld("revision", first)), at)
    val two =
      countedThen(revisionById(second).getOrElse(throw Rules.notHeld("revision", second)), at)
    if one.view.holder != two.view.holder || one.view.hypothesis != two.view.hypothesis then
      throw Refused(
        "answer.revisions.of-two-beliefs",
        422,
        "The two revisions are of different beliefs, and a change is of one belief.",
        "from" -> s"${one.view.holder} in ${one.view.hypothesis}",
        "to"   -> s"${two.view.holder} in ${two.view.hypothesis}"
      )
    val (from, to) = if one.view.n <= two.view.n then (one, two) else (two, one)
    val before     = from.restsOn.toMap
    val after      = to.restsOn.toMap
    val held       = supports((before.keySet ++ after.keySet).toSeq, at)
    def of(claims: Iterable[String], weights: Map[String, Option[Double]]): Vector[Support] =
      claims.toVector.sorted.flatMap(claim => held.get(claim).map(_.copy(weight = weights(claim))))
    val still = (before.keySet & after.keySet).toVector.sorted.flatMap { claim =>
      held.get(claim).map(s => Still(s.copy(weight = after(claim)), before(claim), after(claim)))
    }
    val between = of(after.keySet, after)
      .flatMap(_.evidence)
      .filter(e => e.dated > from.view.dated && e.dated <= to.view.dated)
      .distinctBy(_.id)
      .sortBy(e => (e.dated.millis, e.id))
    BeliefChange(
      from.view,
      to.view,
      of(after.keySet -- before.keySet, after),
      of(before.keySet -- after.keySet, before),
      still,
      between
    )

  /** The claims taking a stance on a hypothesis, with those since revised shown apart. */
  def caseFor(hypothesis: String, stance: String, at: At = At.now): CaseAnswer =
    val edge = stance match
      case "supports"    => "SUPPORTS"
      case "contradicts" => "CONTRADICTS"
      case other =>
        throw Refused(
          "claim.stance.unknown",
          422,
          "A stance is 'supports' or 'contradicts'.",
          "stance" -> other
        )
    val claims = graph.read(
      "MATCH (c:Claim)-[rel]->(y:Element {id: $hypothesis}) " +
        "WHERE type(rel) = $edge AND c.dated <= $asOf AND c.recordedAt <= $by RETURN c.id AS id ORDER BY c.dated, c.id",
      at.parameters ++ Map("hypothesis" -> s"hypothesis:$hypothesis", "edge" -> edge)
    )(row => local(row.string("id")))
    val held                 = supports(claims, at)
    val (revised, unrevised) = claims.flatMap(held.get).partition(_.revisedBy.nonEmpty)
    CaseAnswer(unrevised, revised)

  /**
   * What was learned about a question after a time: evidence, claims and belief revisions, oldest
   * first.
   */
  def learned(question: String, after: Moment, at: At = At.now): Learned =
    val parameters =
      at.parameters ++ Map("question" -> s"question:$question", "after" -> after.millis)
    val about = "MATCH (q:Element {id: $question})<-[:ANSWERS]-(y:Hypothesis)"
    val claims = graph.read(
      s"$about<-[:SUPPORTS|CONTRADICTS]-(c:Claim) WHERE c.dated > $$after AND c.dated <= $$asOf AND c.recordedAt <= $$by " +
        "WITH DISTINCT c RETURN properties(c) AS claim, [(c)-[:STATED_BY]->(h) | h.id][0] AS holder ORDER BY c.dated, c.id",
      parameters
    )(row => claimView(row.properties("claim"), row.stringOption("holder")))
    val evidence = graph.read(
      s"$about<-[:SUPPORTS|CONTRADICTS]-(c:Claim)-[:DERIVES_FROM]->(e:Evidence) " +
        "WHERE c.dated <= $asOf AND c.recordedAt <= $by AND e.dated > $after AND e.dated <= $asOf AND e.recordedAt <= $by " +
        "WITH DISTINCT e RETURN properties(e) AS evidence, [(e)-[:FROM_SOURCE]->(s) | properties(s)][0] AS source ORDER BY e.dated, e.id",
      parameters
    )(row =>
      evidenceView(
        row.properties("evidence"),
        row.propertiesOption("source").filter(_.contains("name"))
      )
    )
    val revisions = graph.read(
      s"$about<-[:BELIEF_IN]-(r:BeliefRevision) WHERE r.dated > $$after AND r.dated <= $$asOf AND r.recordedAt <= $$by " +
        s"WITH DISTINCT r ORDER BY r.dated, r.id RETURN $RevisionColumns",
      parameters
    )(row => stated(row).view)
    Learned(evidence, claims, revisions)

  /** Two holders' beliefs in one hypothesis, and what each rests on. */
  def comparison(hypothesis: String, a: String, b: String, at: At = At.now): Comparison =
    val one  = revisionThen(a, hypothesis, at)
    val two  = revisionThen(b, hypothesis, at)
    val ofA  = one.fold(Map.empty[String, Option[Double]])(_.restsOn.toMap)
    val ofB  = two.fold(Map.empty[String, Option[Double]])(_.restsOn.toMap)
    val held = supports((ofA.keySet ++ ofB.keySet).toSeq, at)
    def only(claims: Set[String], weights: Map[String, Option[Double]]): Vector[Support] =
      claims.toVector.sorted.flatMap(claim => held.get(claim).map(_.copy(weight = weights(claim))))
    val both = (ofA.keySet & ofB.keySet).toVector.sorted.flatMap { claim =>
      held.get(claim).map(s => Shared(s, ofA(claim), ofB(claim)))
    }
    val difference =
      for
        x <- one
        y <- two
      yield (BigDecimal(x.view.probability) - BigDecimal(y.view.probability)).abs.toDouble
    Comparison(
      Side(a, one.map(_.view), one.forall(_.restsOn.isEmpty)),
      Side(b, two.map(_.view), two.forall(_.restsOn.isEmpty)),
      difference,
      both,
      only(ofA.keySet -- ofB.keySet, ofA),
      only(ofB.keySet -- ofA.keySet, ofB)
    )

  /**
   * The beliefs about a question whose current revision rests on a claim that has since been
   * revised.
   */
  def restingOnRevised(question: String, at: At = At.now): RestingOnRevised =
    val resting = graph.read(
      "MATCH (q:Element {id: $question})<-[:ANSWERS]-(:Hypothesis)<-[:BELIEF_IN]-(r:BeliefRevision) " +
        s"WHERE r.dated <= $$asOf AND r.recordedAt <= $$by AND $CurrentThen AND " +
        "EXISTS { MATCH (r)-[:RESTS_ON]->(:Claim)<-[:REVISES]-(later:Claim) WHERE later.dated <= $asOf AND later.recordedAt <= $by } " +
        s"WITH r ORDER BY r.id RETURN $RevisionColumns",
      at.parameters + ("question" -> s"question:$question")
    )(stated)
    RestingOnRevised(resting.map { belief =>
      val held = supports(belief.restsOn.map(_._1), at)
      val revised = belief.restsOn
        .flatMap((claim, weight) => held.get(claim).map(_.copy(weight = weight)))
        .filter(_.revisedBy.nonEmpty)
      RestingBelief(belief.view.holder, belief.view.hypothesis, belief.view, revised)
    })

object BeliefAnswers:

  /** The columns every query that returns a belief revision returns, for `stated` to read. */
  val RevisionColumns: String =
    "properties(r) AS revision, [(r)-[:HELD_BY]->(h) | h.id][0] AS holder, [(r)-[:BELIEF_IN]->(y) | y.id][0] AS hypothesis, " +
      "[(r)-[:FOLLOWS]->(p) | p.id][0] AS follows, [(r)-[o:RESTS_ON]->(c) | {claim: c.id, weight: o.weight}] AS restsOn"

  /** A revision as the graph has it, with the claims it rests on and the weight of each. */
  final case class Stated(view: RevisionView, restsOn: Vector[(String, Option[Double])])

  /** A node's id without its kind: `claim:c1` is `c1`. */
  def local(id: String): String = id.dropWhile(_ != ':').drop(1)

  def map(value: Any): Map[String, Any] = value.asInstanceOf[Map[String, Any]]

  def text(properties: Map[String, Any], name: String): Option[String] =
    properties.get(name).flatMap(Option(_)).map(_.toString)

  def moment(properties: Map[String, Any], name: String): Option[Moment] =
    properties.get(name).flatMap(Option(_)).map(v => Moment(v.asInstanceOf[Number].longValue))

  def flag(properties: Map[String, Any], name: String): Boolean =
    properties.get(name).contains(true)

  def claimView(properties: Map[String, Any], holder: Option[String]): ClaimView =
    ClaimView(
      local(properties("id").toString),
      holder.map(local),
      text(properties, "statement"),
      moment(properties, "dated").getOrElse(Moment(0)),
      moment(properties, "recordedAt").getOrElse(Moment(0)),
      flag(properties, "withdrawn"),
      moment(properties, "withdrawnAt")
    )

  def evidenceView(properties: Map[String, Any], source: Option[Map[String, Any]]): EvidenceView =
    EvidenceView(
      local(properties("id").toString),
      source.map(s =>
        SourceView(
          local(s("id").toString),
          s("name").toString,
          moment(s, "dated").getOrElse(Moment(0)),
          moment(s, "recordedAt").getOrElse(Moment(0))
        )
      ),
      text(properties, "locator"),
      text(properties, "excerpt"),
      text(properties, "author"),
      moment(properties, "publishedAt"),
      moment(properties, "dated").getOrElse(Moment(0)),
      moment(properties, "recordedAt").getOrElse(Moment(0)),
      flag(properties, "withdrawn"),
      moment(properties, "withdrawnAt")
    )

  def stated(row: Row): Stated =
    val properties = row.properties("revision")
    val view = RevisionView(
      local(properties("id").toString),
      row.stringOption("holder").map(local).getOrElse(""),
      row.stringOption("hypothesis").map(local).getOrElse(""),
      properties("n").asInstanceOf[Number].intValue,
      properties("probability").asInstanceOf[Number].doubleValue,
      row.stringOption("follows").map(local),
      moment(properties, "dated").getOrElse(Moment(0)),
      moment(properties, "recordedAt").getOrElse(Moment(0))
    )
    val restsOn = row.propertiesList("restsOn").map { rest =>
      local(rest("claim").toString) -> rest
        .get("weight")
        .flatMap(Option(_))
        .map(_.asInstanceOf[Number].doubleValue)
    }
    Stated(view, restsOn.sortBy(_._1))
