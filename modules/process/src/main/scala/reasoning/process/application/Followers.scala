package reasoning.process.application

import com.thinkmorestupidless.ankka.agent.blueprint.{
  BlueprintEntity,
  BlueprintEvent,
  RunEntity,
  RunEvent
}
import com.thinkmorestupidless.ankka.core.{ComponentId, EntityId}
import com.thinkmorestupidless.ankka.sdk.*
import reasoning.belief.domain.Moment
import reasoning.process.domain.*

/**
 * What this service holds of ankka's blueprints and runs, from ankka's own events: a version when
 * ankka registers one, a run when one starts, its ending when it ends. Each is held through the
 * recorder, so a version is held before its run and a run before its ending, and a change handed
 * over twice is a repeat.
 */
object Followers:

  /** The version of a blueprint ankka holds, as this service holds it. */
  def versionOf(
      client: ComponentClient,
      blueprint: String,
      number: Int,
      recordedAt: Moment,
      writer: String
  ): Option[BlueprintVersion] =
    client
      .forEventSourcedEntity(EntityId(blueprint))
      .call(BlueprintEntity.get)
      .invoke()
      .version(number)
      .map(v =>
        BlueprintVersion(
          Ids.version(blueprint, number),
          blueprint,
          number,
          v.digest,
          Moment(v.registeredAt),
          recordedAt,
          writer
        )
      )

final class BlueprintVersions(recorder: ProcessRecorder, writer: String)
    extends Consumer[BlueprintEvent, Nothing]:

  def onMessage(event: BlueprintEvent): Effect = event match
    case BlueprintEvent.VersionRegistered(number, _, digest, at) =>
      val blueprint = messageContext.subject
      recorder.holdVersion(
        BlueprintVersion(
          Ids.version(blueprint, number),
          blueprint,
          number,
          digest,
          Moment(at),
          recorder.now(),
          writer
        )
      ): Unit
      effects.done()
    case _ => effects.ignore()

object BlueprintVersions:
  final class Of(recorder: ConsumerContext => ProcessRecorder, writer: String)
      extends Consumer.Companion[BlueprintVersions, BlueprintEvent, Nothing](
        componentId = ComponentId("blueprint-versions"),
        source = ChangeSource.eventsOf(BlueprintEntity)
      ):
    def create(ctx: ConsumerContext) = new BlueprintVersions(recorder(ctx), writer)

final class RunFollower(client: ComponentClient, recorder: ProcessRecorder, writer: String)
    extends Consumer[RunEvent, Nothing]:

  def onMessage(event: RunEvent): Effect = event match
    case RunEvent.Started(blueprint, version, _, startedBy, _, at) =>
      val now = recorder.now()
      // The version may not have reached this service's records yet: it is ankka's, so read it there.
      if recorder.findVersion(blueprint, version).isEmpty then
        Followers
          .versionOf(client, blueprint, version, now, writer)
          .foreach(recorder.holdVersion(_): Unit)
      recorder.holdRun(
        Run(messageContext.subject, blueprint, version, startedBy, Moment(at), now, writer)
      ): Unit
      effects.done()
    case RunEvent.Ended(status, reason, at) =>
      RunStatus.fromAnkka(status) match
        case None => effects.ignore()
        case Some(ended) =>
          recorder.holdEnding(
            RunEnding(messageContext.subject, ended, reason, Moment(at), recorder.now(), writer)
          ): Unit
          effects.done()
    case _ => effects.ignore()

object RunFollower:
  final class Of(recorder: ConsumerContext => ProcessRecorder, writer: String)
      extends Consumer.Companion[RunFollower, RunEvent, Nothing](
        componentId = ComponentId("run-follower"),
        source = ChangeSource.eventsOf(RunEntity)
      ):
    def create(ctx: ConsumerContext) = new RunFollower(ctx.componentClient, recorder(ctx), writer)
