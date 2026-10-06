package reasoning.market.application

import com.thinkmorestupidless.ankka.testkit.KeyValueEntityTestKit
import reasoning.belief.application.Put
import reasoning.belief.domain.{Moment, Outcome}
import reasoning.market.domain.*

/** A market alone: opened once, and its resolutions in one line. */
class MarketEntitySuite extends munit.FunSuite:

  private val t1 = Moment(1_000L)
  private val t2 = Moment(2_000L)
  private val t3 = Moment(3_000L)

  private val market =
    Market(
      "m",
      "q",
      "a venue",
      Vector(Offer("YES", "q/yes"), Offer("NO", "q/no")),
      "criteria",
      t3,
      "market.m",
      Vector.empty,
      t1,
      t1,
      "w"
    )

  private def resolution(
      id: String,
      outcome: Option[String],
      revises: Option[String],
      dated: Moment = t2
  ) =
    Resolution(id, outcome, Vector("e"), "the venue", revises, dated, dated, "w")

  private def opened =
    val kit = KeyValueEntityTestKit.of(MarketEntity, "m")
    assertEquals(
      kit.call(MarketEntity.open)(Put(market, datedStated = true)).replyValue,
      Outcome.created(market)
    )
    kit

  test("a market is opened once; the same again is the one held; another is refused") {
    val kit = opened
    assertEquals(
      kit
        .call(MarketEntity.open)(
          Put(market.copy(writer = "other", recordedAt = t2), datedStated = true)
        )
        .replyValue,
      Outcome.repeat(market)
    )
    val other = kit
      .call(MarketEntity.open)(
        Put(market.copy(resolutionCriteria = "other criteria"), datedStated = true)
      )
      .replyValue
    assertEquals(other.refusal.map(_.rule), Some("id.held-by-another"))
    assertEquals(kit.currentState.held, Some(market))
  }

  test("a market that is not held is not resolved") {
    val kit = KeyValueEntityTestKit.of(MarketEntity, "missing")
    val refused = kit
      .call(MarketEntity.resolve)(Put(resolution("r1", Some("YES"), None), datedStated = true))
      .replyValue
    assertEquals(refused.refusal.map(_.rule), Some("market.not-held"))
  }

  test("a market's resolutions form one line, each revising the one before") {
    val kit   = opened
    val first = resolution("r1", Some("YES"), None)

    // No resolution yet: one that revises something is refused, one that revises none is taken.
    val early = kit
      .call(MarketEntity.resolve)(
        Put(resolution("r1", Some("YES"), Some("r0")), datedStated = true)
      )
      .replyValue
    assertEquals(early.refusal.map(_.rule), Some("market.resolution.revises.not-current"))
    val one = kit.call(MarketEntity.resolve)(Put(first, datedStated = true)).replyValue
    assertEquals(one.created, true)
    assertEquals(one.record.flatMap(_.current), Some(first))

    // The same again is the market as held; a different one under its id is refused.
    assertEquals(
      kit.call(MarketEntity.resolve)(Put(first, datedStated = true)).replyValue.created,
      false
    )
    val changed = kit
      .call(MarketEntity.resolve)(Put(first.copy(outcome = Some("NO")), datedStated = true))
      .replyValue
    assertEquals(changed.refusal.map(_.rule), Some("id.held-by-another"))

    // A second resolution revises the first, and nothing else.
    val beside = kit
      .call(MarketEntity.resolve)(Put(resolution("r2", None, None, t3), datedStated = true))
      .replyValue
    assertEquals(
      beside.refusal.map(r => (r.rule, r.names.get("current"))),
      Some(("market.resolution.revises.not-current", Some("r1")))
    )
    val void   = resolution("r2", None, Some("r1"), t3)
    val second = kit.call(MarketEntity.resolve)(Put(void, datedStated = true)).replyValue
    assertEquals(second.record.map(_.resolutions), Some(Vector(first, void)))
    assertEquals(second.record.flatMap(_.current).map(_.void), Some(true))
    assertEquals(kit.currentState.held.map(_.resolutions.size), Some(2))
  }

  test("a resolution is dated no earlier than its market") {
    val kit = opened
    val refused = kit
      .call(MarketEntity.resolve)(
        Put(resolution("r1", Some("YES"), None, Moment(1L)), datedStated = true)
      )
      .replyValue
    assertEquals(refused.refusal.map(_.rule), Some("market.resolution.dated.not-before-market"))
    assertEquals(kit.currentState.held, Some(market))
  }
