package reasoning.belief.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import reasoning.belief.domain.*

/**
 * What a key value entity of this layer holds: one record, once it has been stated. A record that
 * carries text a writer supplied is held this way and never in an event, because a key value
 * entity's row is overwritten in place and an event is kept for good.
 */
final case class Slot[A](held: Option[A])

/** A record to hold, and whether the writer stated its date or the service supplied it. */
final case class Put[A](record: A, datedStated: Boolean)

// ── Question ────────────────────────────────────────────────────────────────

/** One question, with its hypotheses. A hypothesis may be added; nothing is changed or removed. */
final class QuestionEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Question]]:

  private val id: String = context.entityId

  def emptyState: Slot[Question] = Slot(None)

  def open(put: Put[Question]): Effect[Outcome[Question]] = currentState.held match
    case None =>
      effects.updateState(Slot(Some(put.record))).thenReply(_ => Outcome.created(put.record))
    case Some(held) if Same.question(held, put.record, put.datedStated) =>
      effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("question", id)))

  def addHypothesis(put: Put[Hypothesis]): Effect[Outcome[Question]] = currentState.held match
    case None => effects.reply(Outcome.refused(Rules.questionNotHeld(id)))
    case Some(question) =>
      question.hypothesis(put.record.id) match
        case Some(held) if Same.hypothesis(held, put.record, put.datedStated) =>
          effects.reply(Outcome.repeat(question))
        case Some(_) =>
          effects.reply(Outcome.refused(Rules.heldByAnother("hypothesis", s"$id/${put.record.id}")))
        case None =>
          Rules.hypothesisDatedBeforeQuestion(question, put.record.id, put.record.dated) match
            case Some(refusal) => effects.reply(Outcome.refused(refusal))
            case None =>
              val grown = question.copy(hypotheses = question.hypotheses :+ put.record)
              effects.updateState(Slot(Some(grown))).thenReply(_ => Outcome.created(grown))

  def get: ReadOnlyEffect[Slot[Question]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object QuestionEntity
    extends KeyValueEntity.Companion[QuestionEntity, Slot[Question]](
      componentId = ComponentId("question"),
      stateSerializer = Codecs.serializer[Slot[Question]]("question")
    ):
  given putQuestion: Serializer[Put[Question]] = Codecs.serializer[Put[Question]]("question-put")
  given putHypothesis: Serializer[Put[Hypothesis]] =
    Codecs.serializer[Put[Hypothesis]]("hypothesis-put")
  given outcome: Serializer[Outcome[Question]] =
    Codecs.serializer[Outcome[Question]]("question-outcome")

  def create(context: KeyValueEntityContext) = new QuestionEntity(context)

  val open          = command("open")(_.open)
  val addHypothesis = command("add-hypothesis")(_.addHypothesis)
  val get           = query("get")(_.get)
  val version       = query("version")(_.version)

// ── Holder ──────────────────────────────────────────────────────────────────

/** A writer to add to a holder, and the writer asking. */
final case class AddWriter(writer: String, by: String)

/** One holder. The writer who registers it speaks for it and may add others; none is removed. */
final class HolderEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Holder]]:

  private val id: String = context.entityId

  def emptyState: Slot[Holder] = Slot(None)

  def register(holder: Holder): Effect[Outcome[Holder]] = currentState.held match
    case None => effects.updateState(Slot(Some(holder))).thenReply(_ => Outcome.created(holder))
    case Some(held) if Same.holder(held, holder) => effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("holder", id)))

  def addWriter(add: AddWriter): Effect[Outcome[Holder]] = currentState.held match
    case None =>
      effects.reply(
        Outcome.refused(
          Refusal("holder.not-held", 422, s"No holder '$id' is held.", Map("holder" -> id))
        )
      )
    case Some(holder) =>
      Rules.speaksFor(holder, add.by) match
        case Some(refusal)                          => effects.reply(Outcome.refused(refusal))
        case None if holder.spokenForBy(add.writer) => effects.reply(Outcome.repeat(holder))
        case None =>
          val grown = holder.copy(writers = holder.writers :+ add.writer)
          effects.updateState(Slot(Some(grown))).thenReply(_ => Outcome.created(grown))

  def get: ReadOnlyEffect[Slot[Holder]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object HolderEntity
    extends KeyValueEntity.Companion[HolderEntity, Slot[Holder]](
      componentId = ComponentId("holder"),
      stateSerializer = Codecs.serializer[Slot[Holder]]("holder")
    ):
  given Serializer[Holder]          = Codecs.serializer[Holder]("holder-record")
  given Serializer[AddWriter]       = Codecs.serializer[AddWriter]("holder-add-writer")
  given Serializer[Outcome[Holder]] = Codecs.serializer[Outcome[Holder]]("holder-outcome")

  def create(context: KeyValueEntityContext) = new HolderEntity(context)

  val register  = command("register")(_.register)
  val addWriter = command("add-writer")(_.addWriter)
  val get       = query("get")(_.get)
  val version   = query("version")(_.version)

// ── Source ──────────────────────────────────────────────────────────────────

/** One source. */
final class SourceEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Source]]:

  private val id: String = context.entityId

  def emptyState: Slot[Source] = Slot(None)

  def register(source: Source): Effect[Outcome[Source]] = currentState.held match
    case None => effects.updateState(Slot(Some(source))).thenReply(_ => Outcome.created(source))
    case Some(held) if Same.source(held, source) => effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("source", id)))

  def get: ReadOnlyEffect[Slot[Source]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object SourceEntity
    extends KeyValueEntity.Companion[SourceEntity, Slot[Source]](
      componentId = ComponentId("source"),
      stateSerializer = Codecs.serializer[Slot[Source]]("source")
    ):
  given Serializer[Source]          = Codecs.serializer[Source]("source-record")
  given Serializer[Outcome[Source]] = Codecs.serializer[Outcome[Source]]("source-outcome")

  def create(context: KeyValueEntityContext) = new SourceEntity(context)

  val register = command("register")(_.register)
  val get      = query("get")(_.get)
  val version  = query("version")(_.version)

// ── Evidence ────────────────────────────────────────────────────────────────

/**
 * One piece of evidence, keyed by the digest of its source, locator and excerpt: the same evidence
 * recorded again is this one, whoever sends it and whatever else they say of it.
 */
final class EvidenceEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Evidence]]:

  private val id: String = context.entityId

  def emptyState: Slot[Evidence] = Slot(None)

  def record(evidence: Evidence): Effect[Outcome[Evidence]] = currentState.held match
    case None if evidence.id == id =>
      effects.updateState(Slot(Some(evidence))).thenReply(_ => Outcome.created(evidence))
    case None       => effects.reply(Outcome.refused(Rules.heldByAnother("evidence", id)))
    case Some(held) => effects.reply(Outcome.repeat(held))

  /**
   * Takes the evidence's text out for good: the state is written again without its locator, its
   * excerpt and its author, and nothing keeps the state it replaces. Done once; again, it is the
   * record already withdrawn.
   */
  def withdraw(withdrawal: Withdrawal): Effect[Outcome[Evidence]] = currentState.held match
    case None => effects.reply(Outcome.refused(Rules.notHeld("evidence", id).refusal))
    case Some(held) if held.withdrawn => effects.reply(Outcome.repeat(held))
    case Some(held) =>
      val without =
        held.copy(locator = None, excerpt = None, author = None, withdrawal = Some(withdrawal))
      effects.updateState(Slot(Some(without))).thenReply(_ => Outcome.created(without))

  def get: ReadOnlyEffect[Slot[Evidence]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object EvidenceEntity
    extends KeyValueEntity.Companion[EvidenceEntity, Slot[Evidence]](
      componentId = ComponentId("evidence"),
      stateSerializer = Codecs.serializer[Slot[Evidence]]("evidence")
    ):
  given Serializer[Evidence]          = Codecs.serializer[Evidence]("evidence-record")
  given Serializer[Outcome[Evidence]] = Codecs.serializer[Outcome[Evidence]]("evidence-outcome")

  def create(context: KeyValueEntityContext) = new EvidenceEntity(context)

  given Serializer[Withdrawal] = Codecs.serializer[Withdrawal]("evidence-withdrawal")

  val record   = command("record")(_.record)
  val withdraw = command("withdraw")(_.withdraw)
  val get      = query("get")(_.get)
  val version  = query("version")(_.version)

// ── Claim ───────────────────────────────────────────────────────────────────

/** One claim. A holder who thinks again states another claim that revises this one. */
final class ClaimEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Claim]]:

  private val id: String = context.entityId

  def emptyState: Slot[Claim] = Slot(None)

  def state(put: Put[Claim]): Effect[Outcome[Claim]] = currentState.held match
    case None =>
      effects.updateState(Slot(Some(put.record))).thenReply(_ => Outcome.created(put.record))
    case Some(held) if Same.claim(held, put.record, put.datedStated) =>
      effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("claim", id)))

  /** Takes the claim's statement out for good. Everything else about the claim stays. */
  def withdraw(withdrawal: Withdrawal): Effect[Outcome[Claim]] = currentState.held match
    case None => effects.reply(Outcome.refused(Rules.notHeld("claim", id).refusal))
    case Some(held) if held.withdrawn => effects.reply(Outcome.repeat(held))
    case Some(held) =>
      val without = held.copy(statement = None, withdrawal = Some(withdrawal))
      effects.updateState(Slot(Some(without))).thenReply(_ => Outcome.created(without))

  def get: ReadOnlyEffect[Slot[Claim]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object ClaimEntity
    extends KeyValueEntity.Companion[ClaimEntity, Slot[Claim]](
      componentId = ComponentId("claim"),
      stateSerializer = Codecs.serializer[Slot[Claim]]("claim")
    ):
  given Serializer[Put[Claim]]     = Codecs.serializer[Put[Claim]]("claim-put")
  given Serializer[Outcome[Claim]] = Codecs.serializer[Outcome[Claim]]("claim-outcome")

  def create(context: KeyValueEntityContext) = new ClaimEntity(context)

  given Serializer[Withdrawal] = Codecs.serializer[Withdrawal]("claim-withdrawal")

  val state    = command("state")(_.state)
  val withdraw = command("withdraw")(_.withdraw)
  val get      = query("get")(_.get)
  val version  = query("version")(_.version)
