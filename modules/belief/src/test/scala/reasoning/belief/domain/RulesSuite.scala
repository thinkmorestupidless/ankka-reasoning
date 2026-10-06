package reasoning.belief.domain

/** Every rule of the belief layer, broken once: it names itself and what broke it. */
class RulesSuite extends munit.FunSuite:

  private val t0 = Moment(1_000L)
  private val t1 = Moment(2_000L)

  private val question = Question(
    "q",
    "a question",
    Vector(Hypothesis("yes", "yes", t1, t1, "w"), Hypothesis("no", "no", t1, t1, "w")),
    t1,
    t1,
    "w"
  )
  private val holder   = Holder("h", "agent", "a holder", Vector("w"), t1, t1, "w")
  private val evidence = Evidence("e", "s", Some("l"), Some("x"), None, None, t1, t1, "w")
  private val claim = Claim(
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
  private val revision = Revision("r1", "h", "q/yes", 1, 0.5, Vector.empty, None, t1, t1, "w")

  private def broken(rule: String, names: String*)(check: Option[Refusal])(using
      munit.Location
  ): Unit =
    val refusal = check.getOrElse(fail(s"expected $rule to be broken"))
    assertEquals(refusal.rule, rule)
    names.foreach(name =>
      assert(refusal.names.contains(name), s"$rule does not name '$name': ${refusal.names}")
    )

  private def kept(check: Option[Refusal])(using munit.Location): Unit = assertEquals(check, None)

  test("any record") {
    broken("id.format", "claim", "limit")(Rules.id("claim", "has space"))
    broken("id.format")(Rules.id("claim", "x" * 65))
    broken("id.format")(Rules.id("claim", ""))
    kept(Rules.id("claim", "a.b_c-9"))
    assertEquals(Rules.heldByAnother("claim", "c").rule, "id.held-by-another")
    assertEquals(Rules.heldByAnother("claim", "c").status, 409)
    broken("dated.later-than-now", "claim", "dated")(Rules.notLaterThanNow("claim", "c", t1, t0))
    kept(Rules.notLaterThanNow("claim", "c", t0, t0))
    broken("text.length", "field", "limit")(Rules.text("claim", "c", "statement", "", 10))
    broken("text.length")(Rules.text("claim", "c", "statement", "x" * 11, 10))
    kept(Rules.text("claim", "c", "statement", "x" * 10, 10))
  }

  test("questions and holders") {
    broken("question.hypotheses.at-least-two", "question")(Rules.atLeastTwoHypotheses("q", 1))
    kept(Rules.atLeastTwoHypotheses("q", 2))
    assertEquals(Rules.questionNotHeld("q").rule, "question.not-held")
    broken("question.hypothesis.dated-before-question", "hypothesis")(
      Rules.hypothesisDatedBeforeQuestion(question, "third", t0)
    )
    kept(Rules.hypothesisDatedBeforeQuestion(question, "third", t1))
    broken("holder.kind.not-in-vocabulary", "kind")(Rules.holderKind("h", "oracle", Set("agent")))
    kept(Rules.holderKind("h", "agent", Set("agent")))
    broken("holder.writer.does-not-speak", "holder", "writer")(Rules.speaksFor(holder, "stranger"))
    assertEquals(Rules.speaksFor(holder, "stranger").map(_.status), Some(403))
    kept(Rules.speaksFor(holder, "w"))
  }

  test("evidence") {
    assertEquals(Rules.sourceNotHeld("s").rule, "evidence.source.not-held")
    broken("evidence.observed-before-published", "observedAt", "publishedAt")(
      Rules.observedAfterPublished("e", t0, Some(t1))
    )
    kept(Rules.observedAfterPublished("e", t1, Some(t0)))
    kept(Rules.observedAfterPublished("e", t1, None))
  }

  test("claims") {
    assertEquals(Rules.claimHolderNotHeld("c", "h").rule, "claim.holder.not-held")
    broken("claim.derives-from.none")(Rules.derivesFromSomething("c", Vector.empty))
    assertEquals(Rules.claimEvidenceNotHeld("c", "e").rule, "claim.derives-from.not-held")
    broken("claim.stance.none")(Rules.takesAStance("c", Vector.empty))
    broken("claim.stance.unknown", "stance")(Rules.stanceKnown("c", Stance("q/yes", "ignores")))
    broken("claim.stance.twice-on-one-hypothesis", "hypothesis")(
      Rules.oneStancePerHypothesis(
        "c",
        Vector(Stance("q/yes", Stance.Supports), Stance("q/yes", Stance.Contradicts))
      )
    )
    kept(
      Rules.oneStancePerHypothesis(
        "c",
        Vector(Stance("q/yes", Stance.Supports), Stance("q/no", Stance.Contradicts))
      )
    )
    assertEquals(Rules.claimHypothesisNotHeld("c", "q/x").rule, "claim.stance.hypothesis-not-held")
    assertEquals(Rules.revisedClaimNotHeld("c", "x").rule, "claim.revises.not-held")
    broken("claim.revises.itself")(Rules.revisesAnother("c", Some("c")))
    kept(Rules.revisesAnother("c", Some("other")))
    broken("claim.dated.not-before-hypothesis", "hypothesis")(
      Rules.claimNotBeforeHypothesis("c", t0, "q/yes", question.hypotheses.head)
    )
    kept(Rules.claimNotBeforeHypothesis("c", t1, "q/yes", question.hypotheses.head))
    broken("claim.dated.not-before-evidence", "evidence")(
      Rules.claimNotBeforeEvidence("c", t0, evidence)
    )
    kept(Rules.claimNotBeforeEvidence("c", t1, evidence))
    broken("claim.dated.not-before-revised", "revises")(
      Rules.claimNotBeforeRevised("c2", t0, claim)
    )
  }

  test("beliefs") {
    assertEquals(Rules.beliefHolderNotHeld("r", "h").rule, "belief.holder.not-held")
    assertEquals(Rules.beliefHypothesisNotHeld("r", "q/x").rule, "belief.hypothesis.not-held")
    broken("belief.probability.range")(Rules.probability("r", 1.1))
    broken("belief.probability.range")(Rules.probability("r", -0.1))
    kept(Rules.probability("r", 0.0))
    kept(Rules.probability("r", 1.0))
    broken("belief.weight.range", "claim")(Rules.weight("r", Rest("c", Some(1.5))))
    kept(Rules.weight("r", Rest("c", None)))
    broken("belief.rests-on.claim-twice", "claim")(
      Rules.eachClaimOnce("r", Vector(Rest("c"), Rest("c", Some(0.5))))
    )
    assertEquals(Rules.restedClaimNotHeld("r", "c").rule, "belief.rests-on.not-held")
    broken("belief.rests-on.other-question", "claim", "question")(
      Rules.claimAboutQuestion("r", "other", claim)
    )
    kept(Rules.claimAboutQuestion("r", "q", claim))
    broken("belief.dated.not-before-hypothesis", "hypothesis")(
      Rules.revisionNotBeforeHypothesis("r", t0, "q/yes", question.hypotheses.head)
    )
    broken("belief.dated.not-before-claim", "claim")(Rules.revisionNotBeforeClaim("r", t0, claim))
    broken("belief.dated.not-before-current", "current")(
      Rules.revisionNotBeforeCurrent("r2", t0, revision)
    )
    broken("belief.follows.not-current", "current")(
      Rules.followsCurrent("r2", None, Some(revision))
    )
    broken("belief.follows.not-current")(Rules.followsCurrent("r2", Some("r1"), None))
    assertEquals(Rules.followsCurrent("r2", None, Some(revision)).map(_.status), Some(409))
    kept(Rules.followsCurrent("r2", Some("r1"), Some(revision)))
    kept(Rules.followsCurrent("r1", None, None))
  }

  test("the first rule broken is the one thrown, and nothing is thrown when none is") {
    Rules.require(None, None)
    val thrown =
      intercept[Refused](Rules.require(None, Rules.id("claim", ""), Rules.probability("r", 2.0)))
    assertEquals(thrown.refusal.rule, "id.format")
  }

  test("an identifier, a hypothesis reference and a moment are read as they are written") {
    assertEquals(HypothesisRef.parse("launch/yes"), Some(HypothesisRef("launch", "yes")))
    assertEquals(HypothesisRef.parse("launch"), None)
    assertEquals(HypothesisRef.parse("a/b/c"), None)
    assertEquals(Ids.belief("agent-a", HypothesisRef("launch", "yes")), "agent-a~launch~yes")
    assertEquals(Moment.parse("2026-05-04T09:00:00Z").map(_.iso), Some("2026-05-04T09:00:00Z"))
    assertEquals(Moment.parse("4 May"), None)
    assertEquals(Evidence.digest("s", "l", "x").length, 64)
    assertNotEquals(Evidence.digest("s", "l", "x"), Evidence.digest("s", "l", "y"))
  }

  test("a withdrawal gives a note, and is made by a writer who may") {
    Seq(None, Some(""), Some("   ")).foreach { note =>
      assertEquals(
        Rules.withdrawalNote("evidence", "e", note).map(_.rule),
        Some("withdrawal.note.none"),
        note.toString
      )
    }
    assertEquals(Rules.withdrawalNote("evidence", "e", Some("taken down")), None)
    assertEquals(
      Rules.withdrawalNote("claim", "c", Some("x" * (Rules.NoteLimit + 1))).map(_.rule),
      Some("text.length")
    )
    assertEquals(Rules.mayWithdraw("claim", "c", "w", allowed = true), None)
    val refused = Rules.mayWithdraw("claim", "c", "w", allowed = false)
    assertEquals(
      refused.map(r => (r.rule, r.status, r.names)),
      Some(("withdrawal.writer.may-not", 403, Map("claim" -> "c", "writer" -> "w")))
    )
  }
