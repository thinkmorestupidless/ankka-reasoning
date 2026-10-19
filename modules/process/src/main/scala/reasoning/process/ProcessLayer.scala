package reasoning.process

import com.thinkmorestupidless.ankka.agent.blueprint.{BlueprintContext, BlueprintRegistry}
import com.thinkmorestupidless.ankka.core.ComponentDescriptor
import com.thinkmorestupidless.ankka.http.{Acl, EndpointClients, HttpEndpoint}
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import reasoning.belief.BeliefLayer
import reasoning.belief.application.{BeliefConfig, Clock, Recorder}
import reasoning.graph.{GraphReader, Layer, Vocabulary}
import reasoning.process.application.*
import reasoning.process.application.graph.ProcessGraph

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

  /** What the service's blueprints may name. None is carried yet. */
  def registry(context: BlueprintContext, config: ProcessConfig): BlueprintRegistry =
    val _ = (context, config)
    BlueprintRegistry.empty

  /** Every endpoint the layer serves, behind the ACL the service gives it. None yet. */
  def endpoints(
      clock: Clock,
      belief: BeliefConfig,
      graph: GraphReader,
      acl: Acl
  ): Seq[EndpointClients => HttpEndpoint] =
    val _ = (clock, belief, graph, acl)
    Seq.empty
