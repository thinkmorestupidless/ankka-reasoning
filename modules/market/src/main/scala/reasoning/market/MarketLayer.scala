package reasoning.market

import com.thinkmorestupidless.ankka.core.ComponentDescriptor
import com.thinkmorestupidless.ankka.http.{Acl, EndpointClients, HttpEndpoint}
import reasoning.belief.BeliefLayer
import reasoning.belief.application.{BeliefConfig, Clock}
import reasoning.graph.{GraphReader, Layer, Vocabulary}
import reasoning.market.answers.MarketAnswers
import reasoning.market.api.MarketsEndpoint
import reasoning.market.application.{MarketEntity, MarketGraph, MarketRecorder}

/**
 * The market layer: markets, their outcomes, price observations and resolutions. It sits above the
 * belief layer, writes through it, and changes nothing in it.
 */
object MarketLayer:

  val layer: Layer = MarketVocabulary.layer

  val vocabulary: Vocabulary = MarketVocabulary.vocabulary

  val components: Seq[ComponentDescriptor] = Seq(MarketEntity.descriptor)

  def graphComponents(topic: String): Seq[ComponentDescriptor] = MarketGraph.On(topic).descriptors

  def recorder(clients: EndpointClients, clock: Clock, config: BeliefConfig): MarketRecorder =
    MarketRecorder(clients.componentClient, clock, BeliefLayer.recorder(clients, clock, config))

  def endpoints(
      clock: Clock,
      config: BeliefConfig,
      graph: GraphReader,
      acl: Acl
  ): Seq[EndpointClients => HttpEndpoint] =
    Seq(clients => MarketsEndpoint(recorder(clients, clock, config), MarketAnswers(graph), acl))
