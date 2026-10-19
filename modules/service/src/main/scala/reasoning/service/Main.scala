package reasoning.service

import com.thinkmorestupidless.ankka.agent.{AgentRuntime, ModelProvider}
import com.thinkmorestupidless.ankka.agent.judgment.JudgmentProvider
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
import reasoning.process.{ProcessConfig, ProcessLayer}

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
   *   out to show the belief layer stands alone. The process layer is above it, so goes with it.
   * @param process
   *   whether the process layer is registered; a test leaves it out to show the layers beneath it
   *   stand without it
   */
  def vocabulary(markets: Boolean = true, process: Boolean = true): Vocabulary =
    BeliefLayer.vocabulary ++
      (if markets then MarketLayer.vocabulary else Vocabulary()) ++
      (if markets && process then ProcessLayer.vocabulary else Vocabulary())

  /** What the belief layer is handed: it reads no settings itself. */
  def beliefConfig(settings: Settings, markets: Boolean = true): BeliefConfig =
    BeliefConfig(
      holderKinds = vocabulary(markets).holderKinds.map(_.name).toSet,
      excerptLimit = settings.excerptLimit,
      stewards = settings.stewards
    )

  /** What the process layer is handed. */
  def processConfig(settings: Settings): ProcessConfig =
    val _ = settings
    ProcessConfig()

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
      markets: Boolean = true,
      process: Boolean = true,
      clock: Clock = Clock.system
  ): Seq[ComponentDescriptor] =
    val processing = markets && process
    BeliefLayer.components ++
      (if markets then MarketLayer.components else Seq.empty) ++
      (if processing then
         AgentRuntime.descriptors ++
           ProcessLayer.components(clock, beliefConfig(settings), processConfig(settings))
       else Seq.empty) ++
      (if publishing then BeliefLayer.graphComponents(settings.graphTopic) else Seq.empty) ++
      (if publishing && markets then MarketLayer.graphComponents(settings.graphTopic)
       else Seq.empty) ++
      (if publishing && processing then ProcessLayer.graphComponents(settings.graphTopic)
       else Seq.empty)

  /**
   * ankka's agents, hosting this service's blueprints: their versions and runs are records of this
   * service's own, which the process layer follows. A run asks `model` and `judgments`.
   */
  def agents(
      settings: Settings,
      model: ModelProvider,
      judgments: JudgmentProvider
  ): AgentRuntime =
    AgentRuntime
      .withDefaultModel(model)
      .withJudgments(judgments)
      .withBlueprints(ProcessLayer.registry(_, processConfig(settings)))

  /** The graph database the answers read, or the reader that says there is none. */
  def graph(settings: Settings): GraphReader = settings.graph.fold(GraphReader.none)(Neo4jGraph(_))

  /** Every endpoint the service serves. */
  def endpoints(
      settings: Settings,
      clock: Clock,
      graph: GraphReader,
      publishing: Boolean,
      markets: Boolean = true,
      process: Boolean = true
  ): Seq[EndpointClients => HttpEndpoint] =
    val config     = beliefConfig(settings, markets)
    val writers    = Writers.acl(settings.people)
    val processing = markets && process
    val layers = BeliefLayer.endpoints(clock, config, graph, writers) ++
      (if markets then MarketLayer.endpoints(clock, config, graph, writers) else Seq.empty) ++
      (if processing then ProcessLayer.endpoints(clock, config, graph, writers) else Seq.empty)
    layers :+ { (clients: EndpointClients) =>
      val recorder = BeliefLayer.recorder(clients, clock, config)
      val market   = MarketLayer.recorder(clients, clock, config)
      val runs     = ProcessLayer.recorder(clients, clock, config)
      GraphEndpoint(
        graph,
        vocabulary(markets, process),
        Seq(recorder.published) ++
          (if markets then Seq(market.published) else Seq.empty) ++
          (if processing then Seq(runs.published) else Seq.empty),
        publishing,
        settings.waitLimitMaxMs,
        writers
      )
    }

  /** Whether the environment names a broker, which is ankka's own variable. */
  def publishing(env: Map[String, String] = sys.env): Boolean =
    env.get(ProjectionRuntime.KafkaEnvVar).exists(_.trim.nonEmpty)

  /** The extensions of a deployed service, in the order they start. */
  def extensions(settings: Settings, graph: GraphReader): Seq[RuntimeExtension] =
    Seq(
      ProjectionRuntime.fromEnv(),
      agents(settings, Providers.model(), Providers.judgments()),
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
