package reasoning.process.application.graph

import com.thinkmorestupidless.ankka.core.graph.PropertyValue
import com.thinkmorestupidless.ankka.core.{ComponentDescriptor, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*
import com.thinkmorestupidless.ankka.sdk.graph.GraphConsumer
import reasoning.belief.BeliefVocabulary
import reasoning.belief.application.Slot
import reasoning.graph.{Element, Elements}
import reasoning.process.ProcessVocabulary as V
import reasoning.process.application.*
import reasoning.process.domain.*

/** What each record of the process layer is in the graph: its node and the edges that leave it. */
object ProcessGraphOf:

  def versionNode(blueprint: String, version: Int): String =
    V.BlueprintVersion.id(Ids.version(blueprint, version))

  def runNode(run: String): String = V.Run.id(run)

  /** An act's id is already `act:<hex>`, which is its node's id. */
  def actNode(act: String): String = V.Act.id(act.stripPrefix(s"${V.Act.idPrefix}:"))

  /** The node of the record an act names, in the belief layer. */
  def recordNode(record: RecordRef): String = record.kind match
    case RecordRef.Evidence => BeliefVocabulary.Evidence.id(record.id)
    case RecordRef.Claim    => BeliefVocabulary.Claim.id(record.id)
    case _                  => BeliefVocabulary.BeliefRevision.id(record.id)

  def version(v: BlueprintVersion): Vector[Element] =
    Vector(
      Elements.node(
        V.BlueprintVersion,
        v.id,
        v.dated.millis,
        v.recordedAt.millis,
        Map("blueprint" -> v.blueprint, "version" -> v.version.toLong, "digest" -> v.digest)
      )
    )

  def run(r: Run): Vector[Element] =
    val node = Elements.node(
      V.Run,
      r.id,
      r.dated.millis,
      r.recordedAt.millis,
      Map("blueprint" -> r.blueprint, "version" -> r.version.toLong, "startedBy" -> r.startedBy)
    )
    Vector(node, Elements.edge(V.Of, node.id, versionNode(r.blueprint, r.version)))

  def ending(e: RunEnding): Vector[Element] =
    val node = Elements.node(
      V.RunEnding,
      e.run,
      e.dated.millis,
      e.recordedAt.millis,
      Map[String, PropertyValue]("status" -> e.status.wire) ++
        e.reason.map(r => "reason" -> (r: PropertyValue))
    )
    Vector(node, Elements.edge(V.Ended, node.id, runNode(e.run)))

  def act(a: Act): Vector[Element] =
    val node = Elements.node(
      V.Act,
      a.id.stripPrefix(s"${V.Act.idPrefix}:"),
      a.dated.millis,
      a.recordedAt.millis,
      Map("step" -> a.step, "worker" -> a.worker, "recordKind" -> a.record.kind)
    )
    Vector(
      node,
      Elements.edge(V.In, node.id, runNode(a.run)),
      Elements.edge(V.Wrote, node.id, recordNode(a.record)),
      Elements.edge(V.As, node.id, BeliefVocabulary.Holder.id(a.holder))
    )

/**
 * The process layer's graph consumers: one per kind of record, each publishing its record whole. A
 * record is a function of the state it is given, so a change handled twice publishes equal deltas.
 */
object ProcessGraph:

  final class VersionGraph extends GraphConsumer[Slot[BlueprintVersion]]:
    def onMessage(state: Slot[BlueprintVersion]): Effect =
      state.held.fold(effects.ignore())(r =>
        effects.publish(ProcessGraphOf.version(r).map(Elements.publish(graph, _))*)
      )

  final class RunGraph extends GraphConsumer[Slot[Run]]:
    def onMessage(state: Slot[Run]): Effect =
      state.held.fold(effects.ignore())(r =>
        effects.publish(ProcessGraphOf.run(r).map(Elements.publish(graph, _))*)
      )

  final class EndingGraph extends GraphConsumer[Slot[RunEnding]]:
    def onMessage(state: Slot[RunEnding]): Effect =
      state.held.fold(effects.ignore())(r =>
        effects.publish(ProcessGraphOf.ending(r).map(Elements.publish(graph, _))*)
      )

  final class ActGraph extends GraphConsumer[Slot[Act]]:
    def onMessage(state: Slot[Act]): Effect =
      state.held.fold(effects.ignore())(r =>
        effects.publish(ProcessGraphOf.act(r).map(Elements.publish(graph, _))*)
      )

  /** The consumers, publishing to the topic the service names. */
  final class On(topic: String):
    object Versions
        extends GraphConsumer.Companion[VersionGraph, Slot[BlueprintVersion]](
          ComponentId("blueprint-version-graph"),
          ChangeSource.stateOf(BlueprintVersionEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new VersionGraph
    object Runs
        extends GraphConsumer.Companion[RunGraph, Slot[Run]](
          ComponentId("run-graph"),
          ChangeSource.stateOf(RunEntityRecord),
          topic
        ):
      def create(ctx: ConsumerContext) = new RunGraph
    object Endings
        extends GraphConsumer.Companion[EndingGraph, Slot[RunEnding]](
          ComponentId("run-ending-graph"),
          ChangeSource.stateOf(RunEndingEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new EndingGraph
    object Acts
        extends GraphConsumer.Companion[ActGraph, Slot[Act]](
          ComponentId("act-graph"),
          ChangeSource.stateOf(ActEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new ActGraph

    val descriptors: Seq[ComponentDescriptor] =
      Seq(Versions.descriptor, Runs.descriptor, Endings.descriptor, Acts.descriptor)
