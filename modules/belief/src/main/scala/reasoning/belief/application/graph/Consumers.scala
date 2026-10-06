package reasoning.belief.application.graph

import com.thinkmorestupidless.ankka.core.{ComponentDescriptor, ComponentId}
import com.thinkmorestupidless.ankka.sdk.*
import com.thinkmorestupidless.ankka.sdk.graph.{
  GraphConsumer,
  GraphEffect,
  GraphEffects,
  GraphElements
}
import reasoning.belief.application.*
import reasoning.belief.domain.*
import reasoning.graph.{Element, Elements}

/**
 * The belief layer's graph consumers: one per kind of entity, each publishing its record's node and
 * the edges that leave it, at the version of the change it is handed.
 *
 * A record is a function of the state or the event it is given, so a change handled twice publishes
 * equal deltas and nothing here has to be made safe to repeat.
 */
object Consumers:

  private def publish(
      graph: GraphElements,
      effects: GraphEffects,
      elements: Vector[Element]
  ): GraphEffect =
    effects.publish(elements.map(Elements.publish(graph, _))*)

  final class QuestionGraph extends GraphConsumer[Slot[Question]]:
    def onMessage(state: Slot[Question]): Effect =
      state.held.fold(effects.ignore())(record => publish(graph, effects, GraphOf.question(record)))

  final class HolderGraph extends GraphConsumer[Slot[Holder]]:
    def onMessage(state: Slot[Holder]): Effect =
      state.held.fold(effects.ignore())(record => publish(graph, effects, GraphOf.holder(record)))

  final class SourceGraph extends GraphConsumer[Slot[Source]]:
    def onMessage(state: Slot[Source]): Effect =
      state.held.fold(effects.ignore())(record => publish(graph, effects, GraphOf.source(record)))

  final class EvidenceGraph extends GraphConsumer[Slot[Evidence]]:
    def onMessage(state: Slot[Evidence]): Effect =
      state.held.fold(effects.ignore())(record => publish(graph, effects, GraphOf.evidence(record)))

  final class ClaimGraph extends GraphConsumer[Slot[Claim]]:
    def onMessage(state: Slot[Claim]): Effect =
      state.held.fold(effects.ignore())(record => publish(graph, effects, GraphOf.claim(record)))

  final class BeliefGraph extends GraphConsumer[BeliefEvent]:
    def onMessage(event: BeliefEvent): Effect = event match
      case BeliefEvent.RevisionStated(revision) =>
        publish(graph, effects, GraphOf.revision(revision))

  /** The consumers, publishing to the topic the service names. */
  final class On(topic: String):
    object Questions
        extends GraphConsumer.Companion[QuestionGraph, Slot[Question]](
          ComponentId("question-graph"),
          ChangeSource.stateOf(QuestionEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new QuestionGraph
    object Holders
        extends GraphConsumer.Companion[HolderGraph, Slot[Holder]](
          ComponentId("holder-graph"),
          ChangeSource.stateOf(HolderEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new HolderGraph
    object Sources
        extends GraphConsumer.Companion[SourceGraph, Slot[Source]](
          ComponentId("source-graph"),
          ChangeSource.stateOf(SourceEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new SourceGraph
    object EvidenceRecords
        extends GraphConsumer.Companion[EvidenceGraph, Slot[Evidence]](
          ComponentId("evidence-graph"),
          ChangeSource.stateOf(EvidenceEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new EvidenceGraph
    object Claims
        extends GraphConsumer.Companion[ClaimGraph, Slot[Claim]](
          ComponentId("claim-graph"),
          ChangeSource.stateOf(ClaimEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new ClaimGraph
    object Beliefs
        extends GraphConsumer.Companion[BeliefGraph, BeliefEvent](
          ComponentId("belief-graph"),
          ChangeSource.eventsOf(BeliefEntity),
          topic
        ):
      def create(ctx: ConsumerContext) = new BeliefGraph

    val descriptors: Seq[ComponentDescriptor] =
      Seq(
        Questions.descriptor,
        Holders.descriptor,
        Sources.descriptor,
        EvidenceRecords.descriptor,
        Claims.descriptor,
        Beliefs.descriptor
      )

  def on(topic: String): Seq[ComponentDescriptor] = On(topic).descriptors
