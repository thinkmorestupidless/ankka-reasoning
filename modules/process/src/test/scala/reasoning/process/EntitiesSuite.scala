package reasoning.process

import com.thinkmorestupidless.ankka.testkit.KeyValueEntityTestKit
import reasoning.belief.domain.Outcome
import reasoning.process.Fixtures.*
import reasoning.process.application.*

/** Each record is held once: the same again is a repeat, another under its id is refused. */
class EntitiesSuite extends munit.FunSuite:

  test("a blueprint version is held once") {
    val kit = KeyValueEntityTestKit.of(BlueprintVersionEntity, version.id)
    assertEquals(kit.call(BlueprintVersionEntity.put)(version).replyValue, Outcome.created(version))
    assertEquals(
      kit.call(BlueprintVersionEntity.put)(version.copy(recordedAt = t3)).replyValue,
      Outcome.repeat(version)
    )
    assertEquals(
      kit
        .call(BlueprintVersionEntity.put)(version.copy(digest = "e" * 64))
        .replyValue
        .refusal
        .map(_.rule),
      Some("id.held-by-another")
    )
  }

  test("a run is held once") {
    val kit = KeyValueEntityTestKit.of(RunEntityRecord, run.id)
    assertEquals(kit.call(RunEntityRecord.put)(run).replyValue, Outcome.created(run))
    assertEquals(
      kit.call(RunEntityRecord.put)(run.copy(writer = "local")).replyValue,
      Outcome.repeat(run)
    )
    assertEquals(
      kit.call(RunEntityRecord.put)(run.copy(version = 2)).replyValue.refusal.map(_.rule),
      Some("id.held-by-another")
    )
  }

  test("a run ending is held once") {
    val kit = KeyValueEntityTestKit.of(RunEndingEntity, ending.run)
    assertEquals(kit.call(RunEndingEntity.put)(ending).replyValue, Outcome.created(ending))
    assertEquals(kit.call(RunEndingEntity.put)(ending).replyValue, Outcome.repeat(ending))
    assertEquals(
      kit.call(RunEndingEntity.put)(ending.copy(reason = None)).replyValue.refusal.map(_.rule),
      Some("id.held-by-another")
    )
  }

  test("a record keeps its first act: another run's act for it is answered with the one held") {
    val kit = KeyValueEntityTestKit.of(ActEntity, act.id)
    assertEquals(kit.call(ActEntity.put)(act).replyValue, Outcome.created(act))
    assertEquals(kit.call(ActEntity.put)(act).replyValue, Outcome.repeat(act))
    assertEquals(kit.call(ActEntity.put)(act.copy(run = "r2")).replyValue, Outcome.repeat(act))
    assertEquals(
      kit.call(ActEntity.put)(act.copy(step = "revise")).replyValue.refusal.map(_.rule),
      Some("id.held-by-another")
    )
  }

  test("an act is held only under its own id") {
    val kit = KeyValueEntityTestKit.of(ActEntity, "act:other")
    assertEquals(
      kit.call(ActEntity.put)(act).replyValue.refusal.map(_.rule),
      Some("id.held-by-another")
    )
  }
