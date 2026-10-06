package reasoning.belief

import com.thinkmorestupidless.ankka.core.ComponentDescriptor
import com.thinkmorestupidless.ankka.http.{Acl, EndpointClients, HttpEndpoint}
import reasoning.belief.api.*
import reasoning.belief.application.*
import reasoning.belief.application.graph.Consumers
import reasoning.belief.answers.BeliefAnswers
import reasoning.graph.{GraphReader, Layer, Vocabulary}

/**
 * The belief layer: questions, hypotheses, holders, sources, evidence, claims and beliefs. It names
 * nothing of any layer above it, and this module cannot see one.
 */
object BeliefLayer:

  val layer: Layer = BeliefVocabulary.layer

  /** Every kind of node, edge and holder the layer names. */
  val vocabulary: Vocabulary = BeliefVocabulary.vocabulary

  /**
   * The layer's graph consumers, publishing to `topic`. Registered only when a broker is
   * configured: without one the service holds records and publishes nothing.
   */
  def graphComponents(topic: String): Seq[ComponentDescriptor] = Consumers.on(topic)

  /** Every component the layer hosts. */
  val components: Seq[ComponentDescriptor] = Seq(
    QuestionEntity.descriptor,
    HolderEntity.descriptor,
    SourceEntity.descriptor,
    EvidenceEntity.descriptor,
    ClaimEntity.descriptor,
    ClaimRows.descriptor,
    BeliefEntity.descriptor,
    RevisionRecordEntity.descriptor,
    RevisionRecords.descriptor
  )

  def recorder(clients: EndpointClients, clock: Clock, config: BeliefConfig): Recorder =
    Recorder(clients.componentClient, clock, config)

  /** Every endpoint the layer serves, behind the ACL the service gives it. */
  def endpoints(
      clock: Clock,
      config: BeliefConfig,
      graph: GraphReader,
      acl: Acl
  ): Seq[EndpointClients => HttpEndpoint] = Seq(
    _ => AnswersEndpoint(BeliefAnswers(graph), acl),
    clients => QuestionsEndpoint(recorder(clients, clock, config), acl),
    clients => HoldersEndpoint(recorder(clients, clock, config), acl),
    clients => SourcesEndpoint(recorder(clients, clock, config), acl),
    clients => EvidenceEndpoint(recorder(clients, clock, config), acl),
    clients => ClaimsEndpoint(recorder(clients, clock, config), clients.viewClient, acl),
    clients => BeliefsEndpoint(recorder(clients, clock, config), acl)
  )
