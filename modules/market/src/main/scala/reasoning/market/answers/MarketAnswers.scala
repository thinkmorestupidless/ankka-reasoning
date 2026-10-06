package reasoning.market.answers

import reasoning.belief.answers.{At, BeliefAnswers, EvidenceView, RevisionView}
import reasoning.belief.domain.{Moment, Refused}
import reasoning.graph.GraphReader

/** A resolution as the graph holds it. */
final case class ResolutionView(
    id: String,
    outcome: Option[String],
    void: Boolean,
    authority: String,
    dated: Moment,
    recordedAt: Moment
)

/**
 * Why a market was resolved: the resolution, the hypothesis it came to, and the evidence for it.
 */
final case class WhyResolved(
    resolution: ResolutionView,
    hypothesis: Option[String],
    evidence: Vector[EvidenceView]
)

final case class BeliefAtResolution(
    holder: String,
    hypothesis: String,
    revision: RevisionView,
    resolvedTo: Boolean
)

/** What each holder believed when a market was resolved. */
final case class BeliefsAtResolution(
    resolution: ResolutionView,
    hypothesis: Option[String],
    beliefs: Vector[BeliefAtResolution]
)

/** The market layer's answers, each a walk of the graph that starts from the market's node. */
final class MarketAnswers(graph: GraphReader):

  import BeliefAnswers.*

  /** The market's current resolution at a time: the one no resolution that counts revises. */
  def whyResolved(market: String, at: At = At.now): WhyResolved =
    graph
      .read(
        "MATCH (m:Element {id: $market})<-[:RESOLUTION_OF]-(r:Resolution) " +
          "WHERE r.dated <= $asOf AND r.recordedAt <= $by AND " +
          "NOT EXISTS { MATCH (later:Resolution)-[:REVISES_RESOLUTION]->(r) WHERE later.dated <= $asOf AND later.recordedAt <= $by } " +
          "RETURN properties(r) AS resolution, [(r)-[:RESOLVES_TO]->(y) | y.id][0] AS hypothesis, " +
          "[(r)-[:ON_EVIDENCE]->(e) | {evidence: properties(e), source: [(e)-[:FROM_SOURCE]->(s) | properties(s)][0]}] AS evidence",
        at.parameters + ("market" -> s"market:$market")
      ) { row =>
        val properties = row.properties("resolution")
        val evidence = row.propertiesList("evidence").map { both =>
          evidenceView(
            map(both("evidence")),
            both.get("source").flatMap(Option(_)).map(map).filter(_.contains("name"))
          )
        }
        WhyResolved(
          ResolutionView(
            local(properties("id").toString).dropWhile(_ != '/').drop(1),
            text(properties, "outcome"),
            flag(properties, "void"),
            properties("authority").toString,
            moment(properties, "dated").getOrElse(Moment(0)),
            moment(properties, "recordedAt").getOrElse(Moment(0))
          ),
          row.stringOption("hypothesis").map(local),
          evidence.sortBy(e => (e.dated.millis, e.id))
        )
      }
      .headOption
      .getOrElse(
        throw Refused(
          "market.not-resolved",
          404,
          s"The market '$market' has no resolution.",
          "market" -> market
        )
      )

  /**
   * For each hypothesis the market offers an outcome for, each holder's last belief revision dated
   * no later than the resolution, and whether that hypothesis is the one it resolved to.
   */
  def beliefsAtResolution(market: String, at: At = At.now): BeliefsAtResolution =
    val why      = whyResolved(market, at)
    val resolved = why.resolution.dated.millis.min(at.asOf)
    val beliefs = graph.read(
      "MATCH (m:Element {id: $market})-[:OFFERS]->(y:Hypothesis)<-[:BELIEF_IN]-(r:BeliefRevision) " +
        "WHERE r.dated <= $resolved AND r.recordedAt <= $by AND " +
        "NOT EXISTS { MATCH (n:BeliefRevision)-[:FOLLOWS]->(r) WHERE n.dated <= $resolved AND n.recordedAt <= $by } " +
        s"WITH r ORDER BY r.id RETURN $RevisionColumns",
      Map("market" -> s"market:$market", "resolved" -> resolved, "by" -> at.by)
    )(stated)
    BeliefsAtResolution(
      why.resolution,
      why.hypothesis,
      beliefs.map(b =>
        BeliefAtResolution(
          b.view.holder,
          b.view.hypothesis,
          b.view,
          why.hypothesis.contains(b.view.hypothesis)
        )
      )
    )
