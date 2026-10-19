package reasoning.process.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import reasoning.belief.application.Slot
import reasoning.belief.domain.{Outcome, Rules}
import reasoning.process.domain.*

// Each record of the process layer is one key value entity: held once, the same record again a
// repeat, a different one under its id refused. None is ever written a second time.

// ── Blueprint version ───────────────────────────────────────────────────────

final class BlueprintVersionEntity(context: KeyValueEntityContext)
    extends KeyValueEntity[Slot[BlueprintVersion]]:

  private val id: String = context.entityId

  def emptyState: Slot[BlueprintVersion] = Slot(None)

  def put(version: BlueprintVersion): Effect[Outcome[BlueprintVersion]] = currentState.held match
    case None => effects.updateState(Slot(Some(version))).thenReply(_ => Outcome.created(version))
    case Some(held) if Same.version(held, version) => effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("blueprint version", id)))

  def get: ReadOnlyEffect[Slot[BlueprintVersion]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object BlueprintVersionEntity
    extends KeyValueEntity.Companion[BlueprintVersionEntity, Slot[BlueprintVersion]](
      componentId = ComponentId("blueprint-version"),
      stateSerializer = Codecs.serializer[Slot[BlueprintVersion]]("blueprint-version")
    ):
  given Serializer[BlueprintVersion] =
    Codecs.serializer[BlueprintVersion]("blueprint-version-record")
  given Serializer[Outcome[BlueprintVersion]] =
    Codecs.serializer[Outcome[BlueprintVersion]]("blueprint-version-outcome")

  def create(context: KeyValueEntityContext) = new BlueprintVersionEntity(context)

  val put     = command("put")(_.put)
  val get     = query("get")(_.get)
  val version = query("version")(_.version)

// ── Run ─────────────────────────────────────────────────────────────────────

/** One run. Named so as not to be taken for ankka's own `RunEntity`, whose record it copies. */
final class RunEntityRecord(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Run]]:

  private val id: String = context.entityId

  def emptyState: Slot[Run] = Slot(None)

  def put(run: Run): Effect[Outcome[Run]] = currentState.held match
    case None => effects.updateState(Slot(Some(run))).thenReply(_ => Outcome.created(run))
    case Some(held) if Same.run(held, run) => effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("run", id)))

  def get: ReadOnlyEffect[Slot[Run]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object RunEntityRecord
    extends KeyValueEntity.Companion[RunEntityRecord, Slot[Run]](
      componentId = ComponentId("run"),
      stateSerializer = Codecs.serializer[Slot[Run]]("run")
    ):
  given Serializer[Run]          = Codecs.serializer[Run]("run-record")
  given Serializer[Outcome[Run]] = Codecs.serializer[Outcome[Run]]("run-outcome")

  def create(context: KeyValueEntityContext) = new RunEntityRecord(context)

  val put     = command("put")(_.put)
  val get     = query("get")(_.get)
  val version = query("version")(_.version)

// ── Run ending ──────────────────────────────────────────────────────────────

final class RunEndingEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[RunEnding]]:

  private val id: String = context.entityId

  def emptyState: Slot[RunEnding] = Slot(None)

  def put(ending: RunEnding): Effect[Outcome[RunEnding]] = currentState.held match
    case None => effects.updateState(Slot(Some(ending))).thenReply(_ => Outcome.created(ending))
    case Some(held) if Same.ending(held, ending) => effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("run ending", id)))

  def get: ReadOnlyEffect[Slot[RunEnding]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object RunEndingEntity
    extends KeyValueEntity.Companion[RunEndingEntity, Slot[RunEnding]](
      componentId = ComponentId("run-ending"),
      stateSerializer = Codecs.serializer[Slot[RunEnding]]("run-ending")
    ):
  given Serializer[RunEnding]          = Codecs.serializer[RunEnding]("run-ending-record")
  given Serializer[Outcome[RunEnding]] = Codecs.serializer[Outcome[RunEnding]]("run-ending-outcome")

  def create(context: KeyValueEntityContext) = new RunEndingEntity(context)

  val put     = command("put")(_.put)
  val get     = query("get")(_.get)
  val version = query("version")(_.version)

// ── Act ─────────────────────────────────────────────────────────────────────

final class ActEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Act]]:

  private val id: String = context.entityId

  def emptyState: Slot[Act] = Slot(None)

  /**
   * Holds the act, once. The act of another run for the same record is answered with the one held:
   * a record keeps its first act, and a run gains none for a record it did not write.
   */
  def put(act: Act): Effect[Outcome[Act]] = currentState.held match
    case None if act.id == id =>
      effects.updateState(Slot(Some(act))).thenReply(_ => Outcome.created(act))
    case None => effects.reply(Outcome.refused(Rules.heldByAnother("act", id)))
    case Some(held) if held.run != act.run || Same.act(held, act) =>
      effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("act", id)))

  def get: ReadOnlyEffect[Slot[Act]] = effects.reply(currentState)

  /** The record's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object ActEntity
    extends KeyValueEntity.Companion[ActEntity, Slot[Act]](
      componentId = ComponentId("act"),
      stateSerializer = Codecs.serializer[Slot[Act]]("act")
    ):
  given Serializer[Act]          = Codecs.serializer[Act]("act-record")
  given Serializer[Outcome[Act]] = Codecs.serializer[Outcome[Act]]("act-outcome")

  def create(context: KeyValueEntityContext) = new ActEntity(context)

  val put     = command("put")(_.put)
  val get     = query("get")(_.get)
  val version = query("version")(_.version)
