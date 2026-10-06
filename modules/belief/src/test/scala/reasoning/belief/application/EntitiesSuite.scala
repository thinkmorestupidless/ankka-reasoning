package reasoning.belief.application

import com.thinkmorestupidless.ankka.testkit.{EventSourcedTestKit, KeyValueEntityTestKit}
import reasoning.belief.domain.*

/**
 * Each entity alone: a record is accepted once, the same again is the one held, another is refused.
 */
class EntitiesSuite extends munit.FunSuite:

  private val t1 = Moment(1_000L)
  private val t2 = Moment(2_000L)

  private def question(statement: String = "a question", dated: Moment = t1) =
    Question(
      "q",
      statement,
      Vector(Hypothesis("yes", "yes", dated, t1, "w"), Hypothesis("no", "no", dated, t1, "w")),
      dated,
      t1,
      "w"
    )

  test("a question is opened once; the same again is the one held; another is refused") {
    val kit = KeyValueEntityTestKit.of(QuestionEntity, "q")
    assertEquals(
      kit.call(QuestionEntity.open)(Put(question(), datedStated = true)).replyValue,
      Outcome.created(question())
    )
    val again = kit
      .call(QuestionEntity.open)(
        Put(question().copy(recordedAt = t2, writer = "someone else"), datedStated = true)
      )
      .replyValue
    assertEquals(again, Outcome.repeat(question()))
    // With no date stated the service supplied one, and a different one is not a different record.
    assertEquals(
      kit
        .call(QuestionEntity.open)(Put(question(dated = t2), datedStated = false))
        .replyValue
        .created,
      false
    )
    val other = kit
      .call(QuestionEntity.open)(Put(question("another question"), datedStated = true))
      .replyValue
    assertEquals(other.refusal.map(_.rule), Some("id.held-by-another"))
    assertEquals(kit.currentState.held, Some(question()))
  }

  test("a hypothesis is added; nothing held is changed") {
    val kit = KeyValueEntityTestKit.of(QuestionEntity, "q")
    kit.call(QuestionEntity.open)(Put(question(), datedStated = true)): Unit
    val third = Hypothesis("third", "a third", t2, t2, "w")
    val grown = kit.call(QuestionEntity.addHypothesis)(Put(third, datedStated = true)).replyValue
    assertEquals(grown.record.map(_.hypotheses.map(_.id)), Some(Vector("yes", "no", "third")))
    assertEquals(
      kit.call(QuestionEntity.addHypothesis)(Put(third, datedStated = true)).replyValue.created,
      false
    )
    val reworded = kit
      .call(QuestionEntity.addHypothesis)(
        Put(third.copy(statement = "other words"), datedStated = true)
      )
      .replyValue
    assertEquals(reworded.refusal.map(_.rule), Some("id.held-by-another"))
    val early = kit
      .call(QuestionEntity.addHypothesis)(
        Put(Hypothesis("fourth", "x", Moment(1L), t2, "w"), datedStated = true)
      )
      .replyValue
    assertEquals(early.refusal.map(_.rule), Some("question.hypothesis.dated-before-question"))
    val none = KeyValueEntityTestKit
      .of(QuestionEntity, "missing")
      .call(QuestionEntity.addHypothesis)(Put(third, datedStated = true))
      .replyValue
    assertEquals(none.refusal.map(_.rule), Some("question.not-held"))
  }

  test("a holder's writers are added to by one who speaks for it, and by nobody else") {
    val kit    = KeyValueEntityTestKit.of(HolderEntity, "h")
    val holder = Holder("h", "agent", "a holder", Vector("owner"), t1, t1, "owner")
    assertEquals(kit.call(HolderEntity.register)(holder).replyValue, Outcome.created(holder))
    assertEquals(
      kit.call(HolderEntity.register)(holder.copy(writers = Vector("x"), writer = "x")).replyValue,
      Outcome.repeat(holder)
    )
    assertEquals(
      kit.call(HolderEntity.register)(holder.copy(kind = "model")).replyValue.refusal.map(_.rule),
      Some("id.held-by-another")
    )
    val stranger =
      kit.call(HolderEntity.addWriter)(AddWriter("another", by = "stranger")).replyValue
    assertEquals(stranger.refusal.map(_.rule), Some("holder.writer.does-not-speak"))
    val added = kit.call(HolderEntity.addWriter)(AddWriter("another", by = "owner")).replyValue
    assertEquals(added.record.map(_.writers), Some(Vector("owner", "another")))
    assertEquals(
      kit.call(HolderEntity.addWriter)(AddWriter("another", by = "another")).replyValue.created,
      false
    )
  }

  test("evidence recorded again is the evidence held, whatever else is said of it") {
    val id       = Evidence.digest("s", "l", "x")
    val kit      = KeyValueEntityTestKit.of(EvidenceEntity, id)
    val evidence = Evidence(id, "s", Some("l"), Some("x"), Some("an author"), None, t1, t1, "w")
    assertEquals(kit.call(EvidenceEntity.record)(evidence).replyValue, Outcome.created(evidence))
    val again = kit
      .call(EvidenceEntity.record)(evidence.copy(author = None, dated = t2, writer = "other"))
      .replyValue
    assertEquals(again, Outcome.repeat(evidence))
  }

  test("a claim is stated once") {
    val kit = KeyValueEntityTestKit.of(ClaimEntity, "c")
    val claim = Claim(
      "c",
      "h",
      Some("a claim"),
      Vector("e"),
      Vector(Stance("q/yes", Stance.Supports)),
      None,
      t1,
      t1,
      "w"
    )
    assertEquals(
      kit.call(ClaimEntity.state)(Put(claim, datedStated = true)).replyValue,
      Outcome.created(claim)
    )
    assertEquals(
      kit.call(ClaimEntity.state)(Put(claim, datedStated = true)).replyValue,
      Outcome.repeat(claim)
    )
    Seq(
      claim.copy(statement = Some("other words")),
      claim.copy(derivesFrom = Vector("other")),
      claim.copy(stances = Vector(Stance("q/yes", Stance.Contradicts))),
      claim.copy(revises = Some("earlier")),
      claim.copy(dated = t2)
    ).foreach { changed =>
      val refused = kit.call(ClaimEntity.state)(Put(changed, datedStated = true)).replyValue
      assertEquals(refused.refusal.map(_.rule), Some("id.held-by-another"), changed.toString)
    }
  }

  private def sent(
      id: String,
      follows: Option[String],
      probability: Double = 0.5,
      dated: Moment = t1,
      restsOn: Vector[Rest] = Vector.empty
  ) =
    Revision(id, "h", "q/yes", 0, probability, restsOn, follows, dated, t1, "w")

  private def state(revision: Revision, unlessUnchanged: Boolean = false) =
    StateRevision(revision, datedStated = true, unlessUnchanged)

  test("a belief's revisions form one line") {
    val kit = EventSourcedTestKit.of(BeliefEntity, "h~q~yes")

    // (no revisions) -- a revision that follows none --> current = r1
    val first = kit.call(BeliefEntity.stateRevision)(state(sent("r1", None))).replyValue
    assertEquals(first.created, true)
    assertEquals(first.record.map(r => (r.n, r.sequence)), Some((1, 1L)))

    // current = r1 -- one that follows anything else --> refused, naming r1
    val stale = kit.call(BeliefEntity.stateRevision)(state(sent("r2", None))).replyValue
    assertEquals(stale.refusal.map(_.rule), Some("belief.follows.not-current"))
    assertEquals(stale.refusal.flatMap(_.names.get("current")), Some("r1"))
    val wrong = kit.call(BeliefEntity.stateRevision)(state(sent("r2", Some("r0")))).replyValue
    assertEquals(wrong.refusal.flatMap(_.names.get("current")), Some("r1"))

    // current = r1 -- one that follows r1 --> current = r2
    val second =
      kit.call(BeliefEntity.stateRevision)(state(sent("r2", Some("r1"), 0.6, t2))).replyValue
    assertEquals(second.record.map(r => (r.n, r.sequence, r.follows)), Some((2, 2L, Some("r1"))))

    val head = kit.call(BeliefEntity.get).replyValue
    assertEquals((head.count, head.current.map(_.id)), (2, Some("r2")))
  }

  test(
    "a repeat of the current revision is answered with it, and a different one under its id is refused"
  ) {
    val kit    = EventSourcedTestKit.of(BeliefEntity, "h~q~yes")
    val one    = kit.call(BeliefEntity.stateRevision)(state(sent("r1", None))).replyValue.record.get
    val repeat = kit.call(BeliefEntity.stateRevision)(state(sent("r1", None)))
    assertEquals(repeat.replyValue, Outcome.repeat(one))
    assertEquals(repeat.events, Vector.empty)
    val different =
      kit.call(BeliefEntity.stateRevision)(state(sent("r1", None, probability = 0.9))).replyValue
    assertEquals(different.refusal.map(_.rule), Some("id.held-by-another"))
  }

  test("a revision is not dated before the one it follows") {
    val kit = EventSourcedTestKit.of(BeliefEntity, "h~q~yes")
    kit.call(BeliefEntity.stateRevision)(state(sent("r1", None, dated = t2))): Unit
    val early =
      kit.call(BeliefEntity.stateRevision)(state(sent("r2", Some("r1"), dated = t1))).replyValue
    assertEquals(early.refusal.map(_.rule), Some("belief.dated.not-before-current"))
  }

  test("unless unchanged: the same probability resting on nothing persists nothing") {
    val kit = EventSourcedTestKit.of(BeliefEntity, "h~q~yes")
    val one =
      kit.call(BeliefEntity.stateRevision)(state(sent("r1", None, 0.48))).replyValue.record.get
    val same = kit.call(BeliefEntity.stateRevision)(
      state(sent("r2", Some("r1"), 0.48), unlessUnchanged = true)
    )
    assertEquals(same.replyValue, Outcome.repeat(one))
    assertEquals(same.events, Vector.empty)
    val moved = kit
      .call(BeliefEntity.stateRevision)(state(sent("r3", Some("r1"), 0.52), unlessUnchanged = true))
      .replyValue
    assertEquals(moved.created, true)
    // Without the flag the same number is a new statement of it.
    val restated =
      kit.call(BeliefEntity.stateRevision)(state(sent("r4", Some("r3"), 0.52))).replyValue
    assertEquals(restated.created, true)
  }

  test("evidence's text is withdrawn once, and everything else about it stays") {
    val id       = Evidence.digest("s", "l", "x")
    val kit      = KeyValueEntityTestKit.of(EvidenceEntity, id)
    val evidence = Evidence(id, "s", Some("l"), Some("x"), Some("an author"), Some(t1), t1, t1, "w")
    kit.call(EvidenceEntity.record)(evidence): Unit
    val withdrawal = Withdrawal("taken down", "w", t2)
    val without    = kit.call(EvidenceEntity.withdraw)(withdrawal).replyValue
    val expected =
      evidence.copy(locator = None, excerpt = None, author = None, withdrawal = Some(withdrawal))
    assertEquals(without, Outcome.created(expected))
    assertEquals(kit.currentState.held, Some(expected))
    // A second withdrawal, whoever asks and whatever the note, is the record as withdrawn.
    val again =
      kit.call(EvidenceEntity.withdraw)(Withdrawal("another note", "other", t2)).replyValue
    assertEquals(again, Outcome.repeat(expected))
    // And the same evidence recorded again is the withdrawn record: the text does not come back.
    assertEquals(kit.call(EvidenceEntity.record)(evidence).replyValue, Outcome.repeat(expected))
    val missing = KeyValueEntityTestKit.of(EvidenceEntity, "missing")
    assertEquals(
      missing.call(EvidenceEntity.withdraw)(withdrawal).replyValue.refusal.map(_.rule),
      Some("record.not-held")
    )
  }

  test("a claim's statement is withdrawn once, and its links stay") {
    val kit = KeyValueEntityTestKit.of(ClaimEntity, "c")
    val claim =
      Claim(
        "c",
        "h",
        Some("a claim"),
        Vector("e"),
        Vector(Stance("q/yes", Stance.Supports)),
        Some("b"),
        t1,
        t1,
        "w"
      )
    kit.call(ClaimEntity.state)(Put(claim, datedStated = true)): Unit
    val withdrawal = Withdrawal("named someone", "w", t2)
    val expected   = claim.copy(statement = None, withdrawal = Some(withdrawal))
    assertEquals(kit.call(ClaimEntity.withdraw)(withdrawal).replyValue, Outcome.created(expected))
    assertEquals(
      kit.call(ClaimEntity.withdraw)(Withdrawal("again", "other", t2)).replyValue,
      Outcome.repeat(expected)
    )
    assertEquals(
      kit.currentState.held.map(c => (c.derivesFrom, c.stances, c.revises, c.dated)),
      Some((claim.derivesFrom, claim.stances, claim.revises, claim.dated))
    )
  }
