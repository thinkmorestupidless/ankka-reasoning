package reasoning.process

import com.thinkmorestupidless.ankka.agent.{AgentRuntime, Json}
import com.thinkmorestupidless.ankka.agent.blueprint.{
  BlueprintContext,
  BlueprintRegistry,
  RunSnapshot,
  Shape
}
import com.thinkmorestupidless.ankka.core.ComponentDescriptor
import com.thinkmorestupidless.ankka.http.{Acl, EndpointClients, HttpEndpoint}
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import reasoning.belief.BeliefLayer
import reasoning.belief.application.{BeliefConfig, Clock, Recorder}
import reasoning.graph.{GraphReader, Layer, Vocabulary}
import reasoning.process.api.{ActsEndpoint, RunsEndpoint, RunStarter}
import reasoning.process.application.*
import reasoning.process.application.graph.ProcessGraph
import reasoning.process.appraiser.{Appraiser, GoldenSet}
import reasoning.process.tools.{RunReads, RunResolver, Tools}

import java.nio.file.Path

/**
 * What the process layer is given.
 *
 * @param serviceWriter
 *   the writer of what the service copies from ankka's own records: a blueprint version, a run, a
 *   run ending. No caller over HTTP can be it.
 * @param appraiserHolders
 *   the holders the appraiser may be run for, registered when the service starts
 * @param goldenSetDir
 *   where the golden set's files are, when not on the classpath
 */
final case class ProcessConfig(
    serviceWriter: String = ProcessConfig.ServiceWriter,
    appraiserHolders: Vector[String] = Vector("appraiser-a", "appraiser-b"),
    goldenSetDir: Option[Path] = None
)

object ProcessConfig:
  val ServiceWriter = "service:reasoning"

/**
 * The process layer: blueprint versions, runs, their endings, and the act that ties each record a
 * run wrote to the run. It sits above the market layer, writes the belief layer's records through
 * it, and changes nothing beneath it.
 */
object ProcessLayer:

  val layer: Layer = ProcessVocabulary.layer

  val vocabulary: Vocabulary = ProcessVocabulary.vocabulary

  def recorder(client: ComponentClient, clock: Clock, belief: BeliefConfig): ProcessRecorder =
    ProcessRecorder(client, clock, Recorder(client, clock, belief))

  def recorder(clients: EndpointClients, clock: Clock, belief: BeliefConfig): ProcessRecorder =
    ProcessRecorder(clients.componentClient, clock, BeliefLayer.recorder(clients, clock, belief))

  /** Every component the layer hosts, beside ankka's own blueprint and run entities. */
  def components(
      clock: Clock,
      belief: BeliefConfig,
      config: ProcessConfig
  ): Seq[ComponentDescriptor] =
    val follower = (ctx: com.thinkmorestupidless.ankka.sdk.ConsumerContext) =>
      recorder(ctx.componentClient, clock, belief)
    Seq(
      BlueprintVersionEntity.descriptor,
      RunEntityRecord.descriptor,
      RunEndingEntity.descriptor,
      ActEntity.descriptor,
      ActRows.descriptor,
      BlueprintVersions.Of(follower, config.serviceWriter).descriptor,
      RunFollower.Of(follower, config.serviceWriter).descriptor
    )

  /** The layer's graph consumers, publishing to `topic`. */
  def graphComponents(topic: String): Seq[ComponentDescriptor] = ProcessGraph.On(topic).descriptors

  /**
   * What the service's blueprints may name: the three tools, the appraiser's handlers and question,
   * and the appraiser itself at its first instruction set. Built once as the service starts, when
   * it also registers the appraiser's holders and the golden set's sources.
   *
   * @param reads
   *   ankka's records of runs and versions, read through the runtime that is starting
   */
  def registry(
      context: BlueprintContext,
      clock: Clock,
      belief: BeliefConfig,
      config: ProcessConfig,
      reads: RunReads
  ): BlueprintRegistry =
    val records   = recorder(context.componentClient, clock, belief)
    val goldenSet = GoldenSet(config.goldenSetDir)
    Appraiser.prepare(records, goldenSet, config)
    Appraiser.registry(
      Tools(RunResolver(reads, records, config.serviceWriter), records, goldenSet),
      goldenSet
    )

  /** The blueprints the service carries, by name, and how a run of one is started. */
  def starter(agents: => AgentRuntime): RunStarter = new RunStarter:
    def carried: Map[String, Shape] = Map(Appraiser.Name -> Appraiser.inputShape)
    def start(blueprint: String, input: Json, runId: String, startedBy: String): RunSnapshot =
      agents.runs.start(blueprint, input, runId, startedBy)

  /** Every endpoint the layer serves, behind the ACL the service gives it. */
  def endpoints(
      clock: Clock,
      belief: BeliefConfig,
      graph: GraphReader,
      acl: Acl,
      config: ProcessConfig,
      agents: => AgentRuntime
  ): Seq[EndpointClients => HttpEndpoint] =
    val _ = graph
    Seq(
      clients =>
        val records = recorder(clients, clock, belief)
        RunsEndpoint(
          records,
          clients.viewClient,
          starter(agents),
          RunResolver(RunReads.of(agents), records, config.serviceWriter),
          acl
        )
      ,
      clients => ActsEndpoint(recorder(clients, clock, belief), clients.viewClient, acl)
    )
