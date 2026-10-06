package reasoning.market.domain

import reasoning.belief.domain.Moment

/** Every rule of the market layer as a function: what it lets by, and the name it refuses under. */
class MarketRulesSuite extends munit.FunSuite:

  private val t1 = Moment(1_000L)
  private val t2 = Moment(2_000L)
  private val t3 = Moment(3_000L)

  private val market = Market(
    "m",
    "q",
    "a venue",
    Vector(Offer("YES", "q/yes"), Offer("NO", "q/no")),
    "as the regulator says",
    closesAt = t3,
    Market.holderOf("m"),
    Vector.empty,
    t2,
    t2,
    "w"
  )

  private def resolution(id: String, revises: Option[String], dated: Moment = t3) =
    Resolution(id, Some("YES"), Vector("e"), "the venue", revises, dated, dated, "w")

  test("a market offers at least one outcome, each name once and each hypothesis once") {
    assertEquals(
      MarketRules.someOutcome("m", Vector.empty).map(_.rule),
      Some("market.outcome.none")
    )
    assertEquals(MarketRules.someOutcome("m", market.outcomes), None)
    val named =
      MarketRules.outcomeNamesOnce("m", Vector(Offer("YES", "q/yes"), Offer("YES", "q/no")))
    assertEquals(
      named.map(r => (r.rule, r.names("outcome"))),
      Some(("market.outcome.name-twice", "YES"))
    )
    assertEquals(MarketRules.outcomeNamesOnce("m", market.outcomes), None)
    val offered =
      MarketRules.hypothesesOnce("m", Vector(Offer("YES", "q/yes"), Offer("AYE", "q/yes")))
    assertEquals(
      offered.map(r => (r.rule, r.names("hypothesis"))),
      Some(("market.outcome.hypothesis-twice", "q/yes"))
    )
    assertEquals(MarketRules.hypothesesOnce("m", market.outcomes), None)
  }

  test("a market is dated no earlier than a hypothesis it offers an outcome for") {
    assertEquals(MarketRules.datedNotBeforeHypothesis("m", t2, "q/yes", t2), None)
    assertEquals(
      MarketRules.datedNotBeforeHypothesis("m", t1, "q/yes", t2).map(_.rule),
      Some("market.dated.not-before-hypothesis")
    )
  }

  test("a price is from nought to one, and is observed no later than the market closes") {
    Seq(0.0, 0.5, 1.0).foreach(p => assertEquals(MarketRules.price("m", p), None, p.toString))
    Seq(-0.01, 1.01, Double.NaN).foreach(p =>
      assertEquals(MarketRules.price("m", p).map(_.rule), Some("market.price.range"), p.toString)
    )
    assertEquals(MarketRules.beforeClose(market, t3), None)
    val late = MarketRules.beforeClose(market, Moment(3_001L))
    assertEquals(late.map(_.rule), Some("market.price.after-close"))
    assertEquals(late.flatMap(_.names.get("closesAt")), Some(t3.iso))
  }

  test("a resolution names evidence, and is dated no earlier than it or than its market") {
    assertEquals(
      MarketRules.someEvidence("m", "r", Vector.empty).map(_.rule),
      Some("market.resolution.evidence.none")
    )
    assertEquals(MarketRules.someEvidence("m", "r", Vector("e")), None)
    assertEquals(MarketRules.resolutionNotBeforeEvidence("m", "r", t2, "e", t2), None)
    assertEquals(
      MarketRules.resolutionNotBeforeEvidence("m", "r", t1, "e", t2).map(_.rule),
      Some("market.resolution.dated.not-before-evidence")
    )
    assertEquals(MarketRules.resolutionNotBeforeMarket(market, "r", t2), None)
    assertEquals(
      MarketRules.resolutionNotBeforeMarket(market, "r", t1).map(_.rule),
      Some("market.resolution.dated.not-before-market")
    )
  }

  test("a resolution revises the market's current resolution, and none when there is none") {
    assertEquals(MarketRules.revisesCurrent(market, "r1", None), None)
    val none = MarketRules.revisesCurrent(market, "r1", Some("r0"))
    assertEquals(
      none.map(r => (r.rule, r.status)),
      Some(("market.resolution.revises.not-current", 409))
    )
    assertEquals(none.flatMap(_.names.get("current")), None)

    val resolved = market.copy(resolutions = Vector(resolution("r1", None)))
    assertEquals(MarketRules.revisesCurrent(resolved, "r2", Some("r1")), None)
    Seq(None, Some("r0")).foreach { revises =>
      val refused = MarketRules.revisesCurrent(resolved, "r2", revises)
      assertEquals(refused.flatMap(_.names.get("current")), Some("r1"), revises.toString)
    }
  }

  test("the identifiers a market derives stay within an identifier's length") {
    val longest = "m" * Market.IdLimit
    assert(Market.holderOf(longest).length <= 64)
    assert(
      Market
        .observation(longest, "o" * Market.OutcomeLimit, Moment(9_999_999_999_999L))
        .length <= 64
    )
  }

  test("a market sent again is the one held when what the writer said of it is the same") {
    assert(
      SameMarket.market(
        market,
        market.copy(recordedAt = t3, writer = "other", dated = t3),
        datedStated = false
      )
    )
    assert(!SameMarket.market(market, market.copy(dated = t3), datedStated = true))
    assert(!SameMarket.market(market, market.copy(venue = "another venue"), datedStated = true))
    assert(
      !SameMarket.market(
        market,
        market.copy(outcomes = market.outcomes.reverse),
        datedStated = true
      )
    )
    val held = resolution("r1", None)
    assert(
      SameMarket.resolution(
        held,
        held.copy(evidence = Vector("e"), writer = "other"),
        datedStated = true
      )
    )
    assert(!SameMarket.resolution(held, held.copy(outcome = None), datedStated = true))
  }
