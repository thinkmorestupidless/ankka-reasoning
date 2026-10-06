package reasoning.belief.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import reasoning.belief.domain.*

/** What a belief keeps in memory: its current revision and how many it has had. Never the line. */
final case class BeliefState(current: Option[Revision], count: Int)

/** What has happened to a belief. An event carries numbers, identifiers and times, and no text. */
enum BeliefEvent:
  case RevisionStated(revision: Revision)

/**
 * A revision to state.
 *
 * @param unlessUnchanged
 *   answer with the current revision, and persist nothing, when this one has the same probability
 *   and both rest on no claims: what a holder whose view is only a number uses to say "still this"
 */
final case class StateRevision(revision: Revision, datedStated: Boolean, unlessUnchanged: Boolean)

/**
 * One holder's belief in one hypothesis: the line of its revisions.
 *
 * It is event sourced where every other record is not, because it is the one thing with an order
 * that must hold when two writers race: a revision is accepted only when it follows the current
 * one, and this entity is the single place that knows which that is.
 */
final class BeliefEntity(context: EventSourcedEntityContext)
    extends EventSourcedEntity[BeliefState, BeliefEvent]:

  private val id: String = context.entityId

  def emptyState: BeliefState = BeliefState(None, 0)

  def applyEvent(event: BeliefEvent): BeliefState = event match
    case BeliefEvent.RevisionStated(revision) => BeliefState(Some(revision), currentState.count + 1)

  def stateRevision(command: StateRevision): Effect[Outcome[Revision]] =
    val sent    = command.revision
    val current = currentState.current
    current match
      case Some(held) if held.id == sent.id =>
        if Same.revision(held, sent, command.datedStated) then effects.reply(Outcome.repeat(held))
        else effects.reply(Outcome.refused(Rules.heldByAnother("revision", sent.id)))
      case _ =>
        val broken = Rules
          .followsCurrent(sent.id, sent.follows, current)
          .orElse(current.flatMap(c => Rules.revisionNotBeforeCurrent(sent.id, sent.dated, c)))
        broken match
          case Some(refusal) => effects.reply(Outcome.refused(refusal))
          case None =>
            val unchanged = command.unlessUnchanged && current.exists(c =>
              c.probability == sent.probability && c.restsOn.isEmpty && sent.restsOn.isEmpty
            )
            if unchanged then effects.reply(Outcome.repeat(current.get))
            else
              val stated = sent.copy(
                n = currentState.count + 1,
                sequence = commandContext.sequenceNumber + 1
              )
              effects
                .persist(BeliefEvent.RevisionStated(stated))
                .thenReply(_ => Outcome.created(stated))

  def get: ReadOnlyEffect[BeliefState] =
    val _ = id
    effects.reply(currentState)

object BeliefEntity
    extends EventSourcedEntity.Companion[BeliefEntity, BeliefState, BeliefEvent](
      componentId = ComponentId("belief"),
      stateSerializer = Codecs.serializer[BeliefState]("belief"),
      eventSerializer = Codecs.serializer[BeliefEvent]("belief-event")
    ):
  given Serializer[StateRevision]     = Codecs.serializer[StateRevision]("belief-state-revision")
  given Serializer[Outcome[Revision]] = Codecs.serializer[Outcome[Revision]]("belief-outcome")

  def create(context: EventSourcedEntityContext) = new BeliefEntity(context)

  val stateRevision = command("state-revision")(_.stateRevision)
  val get           = query("get")(_.get)

/**
 * A belief revision, kept where it can be read by its own identifier. The belief's events are the
 * record; this is the copy a reader finds by id and a line is walked through.
 */
final class RevisionRecordEntity(context: KeyValueEntityContext)
    extends KeyValueEntity[Slot[Revision]]:

  def emptyState: Slot[Revision] = Slot(None)

  def put(revision: Revision): Effect[Done] =
    val _ = context
    currentState.held match
      case None    => effects.updateState(Slot(Some(revision))).thenReply(_ => Done)
      case Some(_) => effects.reply(Done)

  def get: ReadOnlyEffect[Slot[Revision]] = effects.reply(currentState)

object RevisionRecordEntity
    extends KeyValueEntity.Companion[RevisionRecordEntity, Slot[Revision]](
      componentId = ComponentId("belief-revision"),
      stateSerializer = Codecs.serializer[Slot[Revision]]("belief-revision")
    ):
  import com.thinkmorestupidless.ankka.core.Serializers.given
  given Serializer[Revision] = Codecs.serializer[Revision]("belief-revision-record")

  def create(context: KeyValueEntityContext) = new RevisionRecordEntity(context)

  val put = command("put")(_.put)
  val get = query("get")(_.get)

/**
 * Writes every revision a belief accepts to its read record. The endpoint writes it too, straight
 * after the command, so a writer can read it back at once; this is what makes sure it is written.
 */
final class RevisionRecords(client: ComponentClient) extends Consumer[BeliefEvent, Nothing]:

  def onMessage(event: BeliefEvent): Effect = event match
    case BeliefEvent.RevisionStated(revision) =>
      client
        .forKeyValueEntity(EntityId(revision.id))
        .call(RevisionRecordEntity.put)
        .invoke(revision): Unit
      effects.done()

object RevisionRecords
    extends Consumer.Companion[RevisionRecords, BeliefEvent, Nothing](
      componentId = ComponentId("belief-revision-records"),
      source = ChangeSource.eventsOf(BeliefEntity)
    ):
  def create(ctx: ConsumerContext) = new RevisionRecords(ctx.componentClient)

/** One row per claim, holding identifiers and its date and never its statement. */
final case class ClaimRow(id: String, revises: String, dated: Long)

/** Which claims revise a claim: the one question a claim cannot answer about itself. */
final class ClaimRowsView extends View[Slot[Claim], ClaimRow]:

  def onChange(state: Slot[Claim]): Effect = state.held match
    case Some(claim) =>
      effects.updateRow(ClaimRow(claim.id, claim.revises.getOrElse(""), claim.dated.millis))
    case None => effects.ignore()

object ClaimRows
    extends View.Companion[ClaimRowsView, Slot[Claim], ClaimRow](
      componentId = ComponentId("claim-rows"),
      source = ChangeSource.stateOf(ClaimEntity),
      rowSerializer = Codecs.serializer[ClaimRow]("claim-row")
    ):
  def create(ctx: ViewComponentContext) = new ClaimRowsView
