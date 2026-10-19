package reasoning.process

import com.thinkmorestupidless.ankka.agent.blueprint.{
  BlueprintEntity,
  BlueprintEvent,
  BlueprintRecord,
  RunEvent,
  RunStatus as AnkkaStatus,
  VersionRecord
}
import com.thinkmorestupidless.ankka.testkit.{ConsumerTestKit, TestTransport}
import reasoning.belief.application.{BeliefConfig, Clock, Recorder, Slot}
import reasoning.belief.domain.{Moment, Outcome}
import reasoning.process.application.*
import reasoning.process.domain.*

import scala.collection.mutable

/**
 * The two followers hold what ankka's events say, through the recorder: a version on its
 * registration, a run and its version on its start, and its ending.
 */
class FollowersSuite extends munit.FunSuite:

  private val writer = "service:reasoning"
  private val now    = Moment(9_000L)
  private val clock  = new Clock { def now(): Moment = FollowersSuite.this.now }

  /** The process layer's entities as one of each, in memory, and ankka's blueprint record. */
  private final class Held:
    val versions = mutable.Buffer.empty[BlueprintVersion]
    val runs     = mutable.Buffer.empty[Run]
    val endings  = mutable.Buffer.empty[RunEnding]
    val transport = TestTransport()
      .stub(BlueprintVersionEntity.put) { v =>
        versions += v; Outcome.created(v)
      }
      .stub(BlueprintVersionEntity.get)(() => Slot(versions.lastOption))
      .stub(RunEntityRecord.put) { r =>
        runs += r; Outcome.created(r)
      }
      .stub(RunEntityRecord.get)(() => Slot(runs.lastOption))
      .stub(RunEndingEntity.put) { e =>
        endings += e; Outcome.created(e)
      }
      .stub(BlueprintEntity.get)(() =>
        BlueprintRecord("appraiser", Vector(VersionRecord(1, "{}", "d" * 64, 1_000L)))
      )
    def recorder = ProcessRecorder(
      transport.client,
      clock,
      Recorder(transport.client, clock, BeliefConfig(Set.empty, 2000, Set.empty))
    )

  test("a version ankka registers is held, dated when ankka registered it") {
    val held = Held()
    ConsumerTestKit
      .of(BlueprintVersions.Of(_ => held.recorder, writer), held.transport.client)
      .onMessage(BlueprintEvent.VersionRegistered(1, "{}", "d" * 64, 1_000L), subject = "appraiser")
    assertEquals(
      held.versions.toList,
      List(BlueprintVersion("appraiser@1", "appraiser", 1, "d" * 64, Moment(1_000L), now, writer))
    )
  }

  test("a run ankka starts is held after its version, which is read from ankka when not yet held") {
    val held = Held()
    ConsumerTestKit
      .of(RunFollower.Of(_ => held.recorder, writer), held.transport.client)
      .onMessage(
        RunEvent.Started("appraiser", 1, "{}", "service:shop/caller", None, 2_000L),
        subject = "r1"
      )
    assertEquals(held.versions.map(_.id).toList, List("appraiser@1"))
    assertEquals(
      held.runs.toList,
      List(Run("r1", "appraiser", 1, "service:shop/caller", Moment(2_000L), now, writer))
    )
  }

  test("a run's ending is held with ankka's status and reason; a step's progress is not") {
    val held = Held()
    held.versions += Fixtures.version
    held.runs += Fixtures.run.copy(dated = Moment(2_000L))
    val kit = ConsumerTestKit.of(RunFollower.Of(_ => held.recorder, writer), held.transport.client)
    kit.onMessage(RunEvent.StepStarted("read", 2_500L), subject = "r1")
    kit.onMessage(RunEvent.Ended(AnkkaStatus.Failed, Some("why"), 3_000L), subject = "r1")
    assertEquals(
      held.endings.toList,
      List(RunEnding("r1", RunStatus.Failed, Some("why"), Moment(3_000L), now, writer))
    )
  }

  test("ankka's statuses of a run that has not ended are not endings") {
    assertEquals(RunStatus.fromAnkka(AnkkaStatus.Running), None)
    assertEquals(RunStatus.fromAnkka(AnkkaStatus.WaitingForDecision), None)
    assertEquals(RunStatus.fromAnkka(AnkkaStatus.Completed), Some(RunStatus.Completed))
    assertEquals(RunStatus.fromAnkka(AnkkaStatus.Cancelled), Some(RunStatus.Cancelled))
  }
