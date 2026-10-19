package reasoning.process.application

import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import reasoning.belief.application.{Clock, Recorder}
import reasoning.belief.domain.{Recorded, Rules}
import reasoning.graph.Element
import reasoning.process.application.graph.ProcessGraphOf
import reasoning.process.domain.*

/**
 * Holds the process layer's records, after checking what each links to is held: the version before
 * its run, the run before its ending and its acts, the record and the holder before an act. Every
 * check reads an entity by its id, never a view, so links point backwards on the write side.
 */
final class ProcessRecorder(client: ComponentClient, clock: Clock, belief: Recorder):

  def now(): reasoning.belief.domain.Moment = clock.now()

  // ── Reads ─────────────────────────────────────────────────────────────────

  def findVersion(blueprint: String, version: Int): Option[BlueprintVersion] =
    client
      .forKeyValueEntity(EntityId(Ids.version(blueprint, version)))
      .call(BlueprintVersionEntity.get)
      .invoke()
      .held

  def findRun(id: String): Option[Run] =
    client.forKeyValueEntity(EntityId(id)).call(RunEntityRecord.get).invoke().held

  def findEnding(run: String): Option[RunEnding] =
    client.forKeyValueEntity(EntityId(run)).call(RunEndingEntity.get).invoke().held

  def findAct(id: String): Option[Act] =
    client.forKeyValueEntity(EntityId(id)).call(ActEntity.get).invoke().held

  /** Whether the belief layer holds the record an act names. */
  def recordHeld(record: RecordRef): Boolean = record.kind match
    case RecordRef.Evidence => belief.findEvidence(record.id).isDefined
    case RecordRef.Claim    => belief.findClaim(record.id).isDefined
    case RecordRef.Revision => belief.findRevision(record.id).isDefined
    case _                  => false

  /**
   * A record's elements and the version they are published at, for whoever waits for the graph to
   * hold it. `None` when the kind is not one of this layer's or the record is not held.
   */
  def published(kind: String, id: String): Option[(Vector[Element], Long)] =
    def at[A](entity: EntityId => Long)(elements: Vector[Element]) =
      elements -> entity(EntityId(id))
    kind match
      case "blueprint-version" =>
        val held = client.forKeyValueEntity(EntityId(id)).call(BlueprintVersionEntity.get).invoke()
        held.held.map(v =>
          at(e => client.forKeyValueEntity(e).call(BlueprintVersionEntity.version).invoke())(
            ProcessGraphOf.version(v)
          )
        )
      case "run" =>
        findRun(id).map(r =>
          at(e => client.forKeyValueEntity(e).call(RunEntityRecord.version).invoke())(
            ProcessGraphOf.run(r)
          )
        )
      case "run-ending" =>
        findEnding(id).map(r =>
          at(e => client.forKeyValueEntity(e).call(RunEndingEntity.version).invoke())(
            ProcessGraphOf.ending(r)
          )
        )
      case "act" =>
        findAct(id).map(r =>
          at(e => client.forKeyValueEntity(e).call(ActEntity.version).invoke())(
            ProcessGraphOf.act(r)
          )
        )
      case _ => None

  // ── Writes ────────────────────────────────────────────────────────────────

  def holdVersion(version: BlueprintVersion): Recorded[BlueprintVersion] =
    client
      .forKeyValueEntity(EntityId(version.id))
      .call(BlueprintVersionEntity.put)
      .invoke(version)
      .recorded

  /** Holds a run of a version already held. */
  def holdRun(run: Run): Recorded[Run] =
    Rules.require(
      ProcessRules.versionNotHeld(
        run.blueprint,
        run.version,
        findVersion(run.blueprint, run.version)
      )
    )
    client.forKeyValueEntity(EntityId(run.id)).call(RunEntityRecord.put).invoke(run).recorded

  /** Holds how a run held ended, not before it started. */
  def holdEnding(ending: RunEnding): Recorded[RunEnding] =
    val run = findRun(ending.run)
    Rules.require(ProcessRules.runNotHeld(ending.run, run))
    Rules.require(ProcessRules.endingNotBeforeRun(run.get, ending.dated))
    client
      .forKeyValueEntity(EntityId(ending.run))
      .call(RunEndingEntity.put)
      .invoke(ending)
      .recorded

  /**
   * Holds an act: of a run and a version held, as a holder its writer speaks for, of a record held.
   * When another run's act is held for the record, that act is the answer and none is written.
   */
  def holdAct(act: Act): Recorded[Act] =
    Rules.require(
      ProcessRules.runNotHeld(act.run, findRun(act.run)),
      ProcessRules.versionNotHeld(
        act.blueprint,
        act.version,
        findVersion(act.blueprint, act.version)
      )
    )
    Rules.require(Rules.speaksFor(belief.holder(act.holder), act.writer))
    Rules.require(ProcessRules.actRecordNotHeld(act.record, recordHeld(act.record)))
    client.forKeyValueEntity(EntityId(act.id)).call(ActEntity.put).invoke(act).recorded
