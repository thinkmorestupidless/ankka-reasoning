package reasoning.market.application

import com.thinkmorestupidless.ankka.core.{ComponentDescriptor, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*
import com.thinkmorestupidless.ankka.sdk.graph.GraphConsumer
import reasoning.belief.BeliefVocabulary
import reasoning.belief.application.Slot
import reasoning.belief.application.graph.GraphOf
import reasoning.graph.{Element, Elements}
import reasoning.market.MarketVocabulary as V
import reasoning.market.domain.Market

/** What a market is in the graph: its node, its resolutions, and every edge they stated. */
object MarketGraphOf:

  def resolutionNode(market: String, resolution: String): String =
    V.Resolution.id(s"$market/$resolution")

  def market(market: Market): Vector[Element] =
    val node = Elements.node(
      V.Market,
      market.id,
      market.dated.millis,
      market.recordedAt.millis,
      Map(
        "venue"              -> market.venue,
        "resolutionCriteria" -> market.resolutionCriteria,
        "closesAt"           -> market.closesAt.millis
      )
    )
    val offers = market.outcomes.map(o =>
      Elements.edge(
        V.Offers,
        node.id,
        GraphOf.hypothesisNode(o.hypothesis),
        Map("outcome" -> o.outcome)
      )
    )
    val resolutions = market.resolutions.flatMap { resolution =>
      val own = Elements.node(
        V.Resolution,
        s"${market.id}/${resolution.id}",
        resolution.dated.millis,
        resolution.recordedAt.millis,
        Map[String, com.thinkmorestupidless.ankka.core.graph.PropertyValue](
          "authority" -> resolution.authority,
          "void"      -> resolution.void
        ) ++
          resolution.outcome.map(o =>
            "outcome" -> (o: com.thinkmorestupidless.ankka.core.graph.PropertyValue)
          )
      )
      val to = for
        outcome <- resolution.outcome
        offer   <- market.offer(outcome)
      yield Elements.edge(
        V.ResolvesTo,
        own.id,
        GraphOf.hypothesisNode(offer.hypothesis),
        Map("outcome" -> outcome)
      )
      Vector(own, Elements.edge(V.ResolutionOf, own.id, node.id)) ++
        to ++
        resolution.evidence.map(e =>
          Elements.edge(V.OnEvidence, own.id, BeliefVocabulary.Evidence.id(e))
        ) ++
        resolution.revises.map(r =>
          Elements.edge(V.RevisesResolution, own.id, resolutionNode(market.id, r))
        )
    }
    Vector(
      node,
      Elements.edge(V.About, node.id, BeliefVocabulary.Question.id(market.question)),
      Elements.edge(V.SpeaksAs, node.id, BeliefVocabulary.Holder.id(market.holder))
    ) ++ offers ++ resolutions

/** The market layer's graph consumer: a market, whole, at its revision. */
final class MarketGraph extends GraphConsumer[Slot[Market]]:
  def onMessage(state: Slot[Market]): Effect =
    state.held.fold(effects.ignore())(m =>
      effects.publish(MarketGraphOf.market(m).map(Elements.publish(graph, _))*)
    )

object MarketGraph:

  final class On(topic: String):
    object Markets
        extends GraphConsumer.Companion[MarketGraph, Slot[Market]](
          ComponentId("market-graph"),
          ChangeSource.stateOf(MarketEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new MarketGraph
    val descriptors: Seq[ComponentDescriptor] = Seq(Markets.descriptor)
