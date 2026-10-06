package reasoning.service

import com.thinkmorestupidless.ankka.core.ComponentDescriptor
import com.thinkmorestupidless.ankka.http.{EndpointClients, HttpEndpoint, HttpServer}
import com.thinkmorestupidless.ankka.runtime.{
  Ankka,
  ClusterConfig,
  ProjectionRuntime,
  RuntimeExtension
}
import reasoning.belief.BeliefLayer
import reasoning.belief.application.{BeliefConfig, Clock}
import reasoning.graph.{GraphReader, Neo4jGraph, Vocabulary}
import reasoning.market.MarketLayer

/**
 * The whole service definition.
 *
 * Registration is explicit, so `components` is the complete inventory of what the service hosts and
 * `endpoints` the complete list of what it serves. A test starts the same two lists under the test
 * kit with a broker, a clock and a server of its own.
 */
object ReasoningService:

  /**
   * The vocabulary of every registered layer.
   *
   * @param markets
   *   whether the market layer is registered. It always is in a deployed service; a test leaves it
   *   out to show the belief layer stands alone.
   */
  def vocabulary(markets: Boolean = true): Vocabulary =
    if markets then BeliefLayer.vocabulary ++ MarketLayer.vocabulary else BeliefLayer.vocabulary

  /** What the belief layer is handed: it reads no settings itself. */
  def beliefConfig(settings: Settings, markets: Boolean = true): BeliefConfig =
    BeliefConfig(
      holderKinds = vocabulary(markets).holderKinds.map(_.name).toSet,
      excerptLimit = settings.excerptLimit,
      stewards = settings.stewards
    )

  /**
   * Every component the service hosts.
   *
   * @param publishing
   *   whether there is a broker to publish the graph to. Without one the graph consumers are not
   *   registered: the service holds and returns records and publishes nothing.
   */
  def components(
      settings: Settings,
      publishing: Boolean,
      markets: Boolean = true
  ): Seq[ComponentDescriptor] =
    BeliefLayer.components ++
      (if markets then MarketLayer.components else Seq.empty) ++
      (if publishing then BeliefLayer.graphComponents(settings.graphTopic) else Seq.empty) ++
      (if publishing && markets then MarketLayer.graphComponents(settings.graphTopic)
       else Seq.empty)

  /** The graph database the answers read, or the reader that says there is none. */
  def graph(settings: Settings): GraphReader = settings.graph.fold(GraphReader.none)(Neo4jGraph(_))

  /** Every endpoint the service serves. */
  def endpoints(
      settings: Settings,
      clock: Clock,
      graph: GraphReader,
      publishing: Boolean,
      markets: Boolean = true
  ): Seq[EndpointClients => HttpEndpoint] =
    val config = beliefConfig(settings, markets)
    val layers = BeliefLayer.endpoints(clock, config, graph, Writers.acl) ++
      (if markets then MarketLayer.endpoints(clock, config, graph, Writers.acl) else Seq.empty)
    layers :+ { (clients: EndpointClients) =>
      val recorder = BeliefLayer.recorder(clients, clock, config)
      val market   = MarketLayer.recorder(clients, clock, config)
      GraphEndpoint(
        graph,
        vocabulary(markets),
        Seq(recorder.published) ++ (if markets then Seq(market.published) else Seq.empty),
        publishing,
        settings.waitLimitMaxMs,
        Writers.acl
      )
    }

  /** Whether the environment names a broker, which is ankka's own variable. */
  def publishing(env: Map[String, String] = sys.env): Boolean =
    env.get(ProjectionRuntime.KafkaEnvVar).exists(_.trim.nonEmpty)

  /** The extensions of a deployed service, in the order they start. */
  def extensions(settings: Settings, graph: GraphReader): Seq[RuntimeExtension] =
    Seq(
      ProjectionRuntime.fromEnv(),
      HttpServer.of(endpoints(settings, Clock.system, graph, publishing())*)
    )

@main def run(): Unit =
  val config   = ClusterConfig.load()
  val settings = Settings.from(config)
  val graph    = ReasoningService.graph(settings)
  if !ReasoningService.publishing() then
    println(
      "reasoning: no broker is set (ANKKA_KAFKA_BOOTSTRAP_SERVERS), so the graph is not published"
    )
  val builder =
    Ankka.service.registerAll(ReasoningService.components(settings, ReasoningService.publishing()))
  val service = ReasoningService
    .extensions(settings, graph)
    .foldLeft(builder)(_.withExtension(_))
    .start("reasoning", config)

  sys.addShutdownHook {
    graph.close()
    service.terminate(): Unit
  }: Unit
  scala.concurrent.Await
    .result(service.whenTerminated, scala.concurrent.duration.Duration.Inf): Unit
