package reasoning.steps

import reasoning.belief.domain.Moment
import reasoning.support.{Answer, Http}
import ujson.{Arr, Obj}

/**
 * A living feature that reads the reasoning out of the graph: why a belief changed, the case for
 * and against a hypothesis, what was believed at a past time, where two holders differ.
 */
abstract class ExplainFeatureSuite(feature: String) extends GraphFeatureSuite(feature):

  // ── Helpers ───────────────────────────────────────────────────────────────

  protected def holderId(name: String): String = s"${w.p}-$name"

  protected def claimId(statement: String): String =
    if statement == example.PendingStatement then example.pending
    else if statement == example.BarrierGoneStatement then example.barrierGone
    else w.notes.getOrElse(s"claim:$statement", fail(s"no claim says '$statement'"))

  protected def ask(path: String): Answer =
    w.last = http.get(path)
    w.last

  protected def answered: ujson.Value =
    assertEquals(w.last.status, 200, w.last.body)
    w.last.json

  protected def enc(text: String): String = Http.encode(text)

  protected def registerNamed(name: String): String =
    val id = holderId(name)
    ok(http.post("/holders", Obj("id" -> id, "kind" -> "agent", "name" -> name))): Unit
    caughtUp("holder", id)
    id

  /** States a revision of a named holder's belief and waits for the graph to hold it. */
  protected def revise(
      holder: String,
      probability: Double,
      restsOn: Seq[(String, Option[Double])],
      follows: Option[String],
      day: String,
      hypothesis: String = ""
  ): String =
    val id = w.fresh("r")
    val body = Obj(
      "id"          -> id,
      "holder"      -> holder,
      "hypothesis"  -> (if hypothesis.isEmpty then example.yes else hypothesis),
      "probability" -> probability,
      "restsOn" -> Arr.from(restsOn.map { (claim, weight) =>
        val rest = Obj("claim" -> claim)
        weight.foreach(x => rest("weight") = x)
        rest
      }),
      "dated" -> World.date(day)
    )
    follows.foreach(f => body("follows") = f)
    ok(http.post("/beliefs/revisions", body)): Unit
    caughtUp("revision", id)
    id

  protected def claimsIn(supports: ujson.Value): List[String] =
    supports.arr.map(_("claim")("id").str).toList

  /** Every string anywhere in a JSON value. */
  protected def strings(value: ujson.Value): Vector[String] = value match
    case ujson.Str(text)  => Vector(text)
    case ujson.Arr(items) => items.toVector.flatMap(strings)
    case ujson.Obj(items) => items.values.toVector.flatMap(strings)
    case _                => Vector.empty

  /** Every string a held record of the launch example has, and every identifier it is known by. */
  protected def heldStrings: Set[String] =
    val records = Vector(
      s"/questions/${example.question}",
      s"/holders/${example.holder}",
      s"/sources/${example.filings}",
      s"/sources/${example.regulator}",
      s"/evidence/${example.filing}",
      s"/evidence/${example.notice}",
      s"/claims/${example.pending}",
      s"/claims/${example.barrierGone}",
      s"/beliefs/revisions/${example.first}",
      s"/beliefs/revisions/${example.second}"
    ).flatMap(path => strings(held(path)))
    records.toSet ++ Set(example.yes, example.no)

  private def changeBetween(from: String, to: String): Unit =
    ask(s"/answers/belief-change?from=$from&to=$to"): Unit

  // ── Why a belief changed ──────────────────────────────────────────────────

  When(
    "a reader asks why the belief of {string} in {string} changed between its first and second belief revisions"
  ) { (holder: String, hypothesis: String) =>
    assertEquals(holderId(holder), example.holder)
    assertEquals(w.hypotheses(hypothesis), example.yes)
    changeBetween(example.first, example.second)
  }

  When("a reader asks why the belief changed between its first and second belief revisions")(() =>
    changeBetween(example.first, example.second)
  )
  When("a reader asks why the belief changed between any two of its belief revisions")(() =>
    changeBetween(example.second, example.first)
  )
  When("a reader asks why the belief changed between its second and third belief revisions")(() =>
    changeBetween(example.second, w.notes("third"))
  )
  When("a reader asks why the belief changed between its first and third belief revisions")(() =>
    changeBetween(example.first, w.notes("third"))
  )

  Then("the explanation gives the probabilities {string} and {string}") {
    (from: String, to: String) =>
      assertEquals(answered("from")("probability").num, from.toDouble)
      assertEquals(answered("to")("probability").num, to.toDouble)
  }

  Then("the explanation gives the claim {string} as newly rested on") { (statement: String) =>
    val newly = answered("newlyRestedOn").arr
    assertEquals(newly.map(_("claim")("statement").str).toList, List(statement))
    assertEquals(claimsIn(answered("newlyRestedOn")), List(claimId(statement)))
  }

  Then(
    "the explanation gives the claim {string} as no longer rested on, and as revised by the other"
  ) { (statement: String) =>
    val gone = answered("noLongerRestedOn").arr
    assertEquals(gone.map(_("claim")("statement").str).toList, List(statement))
    assertEquals(gone.head("revisedBy").arr.map(_.str).toList, List(example.barrierGone))
  }

  Then("each claim comes with the evidence it derives from and the source of that evidence") { () =>
    val newly = answered("newlyRestedOn").arr.head
    assertEquals(newly("evidence").arr.map(_("id").str).toList, List(example.notice))
    assertEquals(newly("evidence").arr.head("source")("name").str, "the regulator")
    val gone = answered("noLongerRestedOn").arr.head
    assertEquals(gone("evidence").arr.map(_("id").str).toList, List(example.filing))
    assertEquals(gone("evidence").arr.head("source")("name").str, "Company X filings")
  }

  Then(
    "the explanation gives the evidence from the source {string}, dated {string}, as observed between them"
  ) { (source: String, day: String) =>
    val between = answered("observedBetween").arr
    assertEquals(between.map(_("id").str).toList, List(example.notice))
    assertEquals(between.head("source")("name").str, source)
    assertEquals(between.head("dated").str, World.date(day))
  }

  Then("every claim, piece of evidence and source in the explanation is a record that is held") {
    () =>
      val supports = answered("newlyRestedOn").arr ++ answered("noLongerRestedOn").arr ++ answered(
        "stillRestedOn"
      ).arr.map(_("support"))
      assert(supports.nonEmpty)
      supports.foreach { support =>
        val claim = held(s"/claims/${support("claim")("id").str}")("claim")
        assertEquals(support("claim")("statement").str, claim("statement").str)
        support("evidence").arr.foreach { evidence =>
          val record = held(s"/evidence/${evidence("id").str}")
          assertEquals(evidence("excerpt").str, record("excerpt").str)
          assertEquals(
            evidence("source")("name").str,
            held(s"/sources/${evidence("source")("id").str}")("name").str
          )
        }
      }
  }

  Then("every statement in the explanation is a record's own") { () =>
    val allowed = heldStrings
    val foreign =
      strings(answered).filterNot(allowed).filterNot(text => Moment.parse(text).isDefined)
    assertEquals(foreign, Vector.empty, "the explanation holds text no held record has")
  }

  Given(
    "a third belief revision with the probability {string}, resting on the same claim as the second"
  ) { (probability: String) =>
    w.notes("third") = revise(
      example.holder,
      probability.toDouble,
      Seq(example.barrierGone -> None),
      Some(example.second),
      "12 May"
    )
  }

  Then("the explanation says no claim came or went") { () =>
    assertEquals(answered("newlyRestedOn").arr.size, 0)
    assertEquals(answered("noLongerRestedOn").arr.size, 0)
    assertEquals(
      claimsIn(ujson.Arr.from(answered("stillRestedOn").arr.map(_("support")))),
      List(example.barrierGone)
    )
  }

  Given("a third belief revision resting on the same claim as the second with a different weight") {
    () =>
      val second = revise(
        example.holder,
        0.61,
        Seq(example.barrierGone -> Some(0.6)),
        Some(example.second),
        "12 May"
      )
      w.notes("second-weighed") = second
      w.notes("third") =
        revise(example.holder, 0.61, Seq(example.barrierGone -> Some(0.9)), Some(second), "13 May")
  }

  Then("the explanation gives the claim as still rested on, with both weights") { () =>
    // The second revision of the example gave no weight; it is restated with one, and compared.
    changeBetween(w.notes("second-weighed"), w.notes("third"))
    val still = answered("stillRestedOn").arr
    assertEquals(still.map(_("support")("claim")("id").str).toList, List(example.barrierGone))
    assertEquals(still.head("weightFrom").num, 0.6)
    assertEquals(still.head("weightTo").num, 0.9)
  }

  Given("a third belief revision") { () =>
    w.notes("third") =
      revise(example.holder, 0.7, Seq(example.barrierGone -> None), Some(example.second), "12 May")
  }

  Then("the explanation gives every claim that came or went between them") { () =>
    assertEquals(claimsIn(answered("newlyRestedOn")), List(example.barrierGone))
    assertEquals(claimsIn(answered("noLongerRestedOn")), List(example.pending))
    assertEquals(answered("from")("n").num, 1.0)
    assertEquals(answered("to")("n").num, 3.0)
  }

  Given("another holder with a belief in the same hypothesis") { () =>
    w.otherHolder = registerNamed("agent-b")
    w.notes("other-revision") = revise(w.otherHolder, 0.43, Seq.empty, None, "12 May")
  }

  When(
    "a reader asks why a belief changed between a belief revision of one holder and a belief revision of the other"
  ) { () =>
    changeBetween(example.second, w.notes("other-revision"))
  }

  Then("the reader is refused, naming the two beliefs") { () =>
    assertEquals(w.last.status, 422, w.last.body)
    assertEquals(w.last.rule, "answer.revisions.of-two-beliefs")
    assert(
      w.last.names("from").contains(example.holder) || w.last.names("to").contains(example.holder),
      w.last.body
    )
    assert(w.last.names.values.exists(_.contains(w.otherHolder)), w.last.body)
  }

  Given("another holder with a belief resting on the claim {string}") { (statement: String) =>
    w.otherHolder = registerNamed("agent-b")
    w.notes("other-revision") =
      revise(w.otherHolder, 0.3, Seq(claimId(statement) -> None), None, "5 May")
  }

  When("a reader asks for that holder's belief") { () =>
    ask(s"/answers/belief?holder=${w.otherHolder}&hypothesis=${example.yes}"): Unit
  }

  Then(
    "the current belief revision is shown as resting on a revised claim, with the claim that revised it"
  ) { () =>
    assertEquals(answered("revision")("id").str, w.notes("other-revision"))
    val rests = answered("restsOn").arr
    assertEquals(rests.map(_("claim")("id").str).toList, List(example.pending))
    assertEquals(rests.head("revisedBy").arr.map(_.str).toList, List(example.barrierGone))
  }

  When("a reader asks which beliefs about the question rest on a revised claim") { () =>
    ask(s"/answers/resting-on-revised?question=${example.question}"): Unit
  }

  Then("the reader is given that holder's belief and not the belief of {string}") {
    (name: String) =>
      val beliefs = answered("beliefs").arr
      assertEquals(beliefs.map(_("holder").str).toList, List(w.otherHolder))
      assert(!beliefs.exists(_("holder").str == holderId(name)))
      assertEquals(
        beliefs.head("revisedClaims").arr.map(_("claim")("id").str).toList,
        List(example.pending)
      )
  }

  // ── The case for and against ──────────────────────────────────────────────

  private def askCase(hypothesis: String, stance: String, asOf: Option[String] = None): Unit =
    val bound = asOf.fold("")(day => s"&asOf=${World.noon(day)}")
    ask(s"/answers/case?hypothesis=$hypothesis&stance=$stance$bound"): Unit

  When("a reader asks for the case for the hypothesis {string}")((h: String) =>
    askCase(w.hypotheses(h), "supports")
  )
  When("a reader asks for the case against the hypothesis {string}")((h: String) =>
    askCase(w.hypotheses(h), "contradicts")
  )
  When("a reader asks for the case against the hypothesis")(() =>
    askCase(example.yes, "contradicts")
  )
  When("a reader asks for the case for the hypothesis")(() => askCase(w.hypothesis, "supports"))

  Then(
    "the reader is given the claim {string} with the evidence it derives from and the source of that evidence"
  ) { (statement: String) =>
    val claims = answered("claims").arr
    assertEquals(claims.map(_("claim")("statement").str).toList, List(statement))
    assertEquals(claims.head("evidence").arr.map(_("id").str).toList, List(example.notice))
    assertEquals(claims.head("evidence").arr.head("source")("name").str, "the regulator")
  }

  Given("a claim that contradicts the hypothesis {string} and is not revised") {
    (hypothesis: String) =>
      val id = w.fresh("c")
      ok(
        http.post(
          "/claims",
          Obj(
            "id"          -> id,
            "holder"      -> example.holder,
            "statement"   -> "a supplier is not ready",
            "derivesFrom" -> Arr(example.filing),
            "stances" -> Arr(
              Obj("hypothesis" -> w.hypotheses(hypothesis), "stance" -> "contradicts")
            ),
            "dated" -> World.date("5 May")
          )
        )
      ): Unit
      caughtUp("claim", id)
      w.claim = id
  }

  Then(
    "the reader is given that claim with the evidence it derives from and the source of that evidence"
  ) { () =>
    val claims = answered("claims").arr
    assertEquals(claims.map(_("claim")("id").str).toList, List(w.claim))
    assertEquals(claims.head("evidence").arr.map(_("id").str).toList, List(example.filing))
    assertEquals(claims.head("evidence").arr.head("source")("name").str, "Company X filings")
  }

  Then("the claim {string} is not in the case") { (statement: String) =>
    assert(!claimsIn(answered("claims")).contains(claimId(statement)), w.last.body)
    w.notes("apart") = claimId(statement)
  }

  Then("the claim is shown apart as a revised claim, with the claim that revised it") { () =>
    val revised = answered("revisedClaims").arr
    assertEquals(revised.map(_("claim")("id").str).toList, List(w.notes("apart")))
    assertEquals(revised.head("revisedBy").arr.map(_.str).toList, List(example.barrierGone))
  }

  Given("another holder whose current belief revision rests on the claim {string}") {
    (statement: String) =>
      w.otherHolder = registerNamed("agent-b")
      revise(w.otherHolder, 0.7, Seq(claimId(statement) -> None), None, "12 May"): Unit
  }

  Then("the claim is shown with both holders whose current belief revisions rest on it") { () =>
    val claim = answered("claims").arr.find(_("claim")("id").str == example.barrierGone).get
    assertEquals(claim("heldBy").arr.map(_.str).toList, List(example.holder, w.otherHolder).sorted)
  }

  Given("a hypothesis no claim takes a stance on") { () =>
    ok(
      http.post(
        s"/questions/${example.question}/hypotheses",
        Obj("id" -> "later", "statement" -> "it launches next year", "dated" -> World.date("2 May"))
      )
    ): Unit
    caughtUp("question", example.question)
    w.hypothesis = s"${example.question}/later"
  }

  Then("the reader is told that no claim supports it") { () =>
    assertEquals(answered("claims").arr.size, 0)
    assertEquals(answered("revisedClaims").arr.size, 0)
  }

  // ── What was believed at a past time ──────────────────────────────────────

  When("a reader asks what {string} believed about {string} as of {string}") {
    (holder: String, hypothesis: String, day: String) =>
      ask(
        s"/answers/belief?holder=${holderId(holder)}&hypothesis=${w.hypotheses(hypothesis)}&asOf=${World.noon(day)}"
      ): Unit
  }

  Then(
    "the reader is given the belief revision with the probability {string}, resting on the claim {string}"
  ) { (probability: String, statement: String) =>
    assertEquals(answered("revision")("probability").num, probability.toDouble)
    assertEquals(answered("revision")("id").str, example.first)
    assertEquals(claimsIn(answered("restsOn")), List(claimId(statement)))
  }

  When("a reader asks for the case for the hypothesis {string} as of {string}") {
    (h: String, day: String) =>
      askCase(w.hypotheses(h), "supports", Some(day))
      w.notes("asOf") = World.noon(day)
  }

  When("a reader asks for the case against the hypothesis {string} as of {string}") {
    (h: String, day: String) =>
      askCase(w.hypotheses(h), "contradicts", Some(day))
      w.notes("asOf") = World.noon(day)
  }

  Then("the claim {string} is not in the answer") { (statement: String) =>
    assert(!strings(answered).contains(claimId(statement)), w.last.body)
    assert(!strings(answered).contains(statement), w.last.body)
  }

  Then("no evidence dated later than {string} is in the answer") { (day: String) =>
    assert(!strings(answered).contains(example.notice), w.last.body)
    val bound = Moment.parse(World.noon(day)).get
    val later = strings(answered).flatMap(Moment.parse).filter(_ > bound)
    assertEquals(later, Vector.empty, w.last.body)
  }

  Then("the claim {string} is in the case") { (statement: String) =>
    assertEquals(claimsIn(answered("claims")), List(claimId(statement)))
  }

  Then("the claim is not shown as a revised claim") { () =>
    assertEquals(answered("revisedClaims").arr.size, 0)
    assertEquals(answered("claims").arr.head("revisedBy").arr.size, 0)
  }

  Then("the reader is told the holder had no belief then") { () =>
    assertEquals(answered.obj.get("revision"), None)
    assertEquals(answered("restsOn").arr.size, 0)
  }

  private def askLearned(after: String, by: Option[String] = None): Answer =
    val bound = by.fold("")(day => s"&asRecordedBy=${World.noon(day)}")
    ask(s"/answers/learned?question=${example.question}&after=${World.noon(after)}$bound")

  When("a reader asks what was learned about the question after {string}")((day: String) =>
    askLearned(day): Unit
  )

  Then(
    "the reader is given the evidence, the claims and the belief revisions dated later than {string}, in the order they are dated"
  ) { (day: String) =>
    val _ = day
    assertEquals(answered("evidence").arr.map(_("id").str).toList, List(example.notice))
    assertEquals(answered("claims").arr.map(_("id").str).toList, List(example.barrierGone))
    assertEquals(answered("revisions").arr.map(_("id").str).toList, List(example.second))
  }

  /** Evidence and a claim derived from it, both dated one day and recorded on another. */
  private def recordLate(dated: String, today: String): Unit =
    clock.set(Moment.parse(World.noon(today)).get)
    val evidence = ok(
      http.post(
        "/evidence",
        Obj(
          "source"     -> example.filings,
          "locator"    -> s"https://filings.example/${w.fresh("late")}",
          "excerpt"    -> "an earlier filing, found late",
          "observedAt" -> World.date(dated)
        )
      )
    ).json("id").str
    val claim = w.fresh("c")
    ok(
      http.post(
        "/claims",
        Obj(
          "id"          -> claim,
          "holder"      -> example.holder,
          "statement"   -> "the filing had more in it",
          "derivesFrom" -> Arr(evidence),
          "stances"     -> Arr(Obj("hypothesis" -> example.yes, "stance" -> "contradicts")),
          "dated"       -> World.date(dated)
        )
      )
    ): Unit
    clock.set(clockStart)
    caughtUp("evidence", evidence)
    caughtUp("claim", claim)
    w.evidence = evidence
    w.claim = claim

  Given(
    "evidence dated {string} and recorded on {string}, with a claim derived from it dated {string}"
  ) { (dated: String, today: String, claimDated: String) =>
    assertEquals(dated, claimDated)
    recordLate(dated, today)
  }

  Then("the evidence is in the answer, dated {string}") { (day: String) =>
    val evidence = answered("evidence").arr
      .find(_("id").str == w.evidence)
      .getOrElse(fail(s"the evidence is not in the answer: ${w.last.body}"))
    assertEquals(evidence("dated").str, World.date(day))
    w.notes("recordedAt") = evidence("recordedAt").str
  }

  Then("the answer says the evidence was recorded on {string}") { (day: String) =>
    assertEquals(w.notes("recordedAt"), Moment.parse(World.noon(day)).get.iso)
  }

  When("a reader asks what was learned about the question after {string}, as recorded by {string}") {
    (after: String, by: String) =>
      askLearned(after, Some(by)): Unit
  }

  Then("the evidence is not in the answer") { () =>
    assert(!answered("evidence").arr.exists(_("id").str == w.evidence), w.last.body)
    // The answer is not empty for another reason: what had been recorded by then is in it.
    assertEquals(answered("evidence").arr.map(_("id").str).toList, List(example.notice))
  }

  Then("the claim derived from it is not in the answer") { () =>
    assert(!answered("claims").arr.exists(_("id").str == w.claim), w.last.body)
    assertEquals(answered("claims").arr.map(_("id").str).toList, List(example.barrierGone))
  }

  Given(
    "the answer to what was learned about the question after {string}, as recorded by {string}"
  ) { (after: String, by: String) =>
    w.notes("before") = ujson.write(askLearned(after, Some(by)).json, sortKeys = true)
    assertEquals(answered("evidence").arr.map(_("id").str).toList, List(example.notice))
  }

  When("a writer records evidence dated {string}, and today is {string}")(
    (dated: String, today: String) => recordLate(dated, today)
  )

  Then("a reader who asks again, as recorded by {string}, is given the same answer") {
    (by: String) =>
      // The record entered late is in the answer when no such bound is given, and not when it is.
      assert(askLearned("4 May").json("evidence").arr.exists(_("id").str == w.evidence))
      assertEquals(
        ujson.write(askLearned("4 May", Some(by)).json, sortKeys = true),
        w.notes("before")
      )
  }

  // ── Where two holders disagree ────────────────────────────────────────────

  Given("the holder {string} with a belief of {string} in {string}") {
    (name: String, probability: String, hypothesis: String) =>
      w.otherHolder = registerNamed(name)
      w.notes("other-revision") = revise(
        w.otherHolder,
        probability.toDouble,
        Seq.empty,
        None,
        "12 May",
        w.hypotheses(hypothesis)
      )
  }

  private def compare(
      a: String,
      b: String,
      hypothesis: String,
      other: Option[String] = None
  ): Unit =
    val second = other.fold("")(h => s"&hypothesisOfB=$h")
    ask(s"/answers/comparison?hypothesis=$hypothesis&a=$a&b=$b$second"): Unit

  When("a reader compares the beliefs of {string} and {string} in {string}") {
    (a: String, b: String, hypothesis: String) =>
      compare(holderId(a), holderId(b), w.hypotheses(hypothesis))
  }

  When("a reader compares the two beliefs")(() =>
    compare(example.holder, w.otherHolder, example.yes)
  )

  Then("the reader is given the probabilities {string} and {string} and the difference {string}") {
    (a: String, b: String, difference: String) =>
      assertEquals(answered("a")("revision")("probability").num, a.toDouble)
      assertEquals(answered("b")("revision")("probability").num, b.toDouble)
      assertEquals(answered("difference").num, difference.toDouble)
  }

  Then(
    "the claims both rest on, the claims only {string} rests on and the claims only {string} rests on"
  ) { (a: String, b: String) =>
    assertEquals(answered("a")("holder").str, holderId(a))
    assertEquals(answered("b")("holder").str, holderId(b))
    assertEquals(answered("both").arr.size, 0)
    assertEquals(claimsIn(answered("onlyA")), List(example.barrierGone))
    assertEquals(answered("onlyB").arr.size, 0)
  }

  Given("both holders rest on one claim with different weights") { () =>
    revise(
      example.holder,
      0.61,
      Seq(example.barrierGone -> Some(0.9)),
      Some(example.second),
      "13 May"
    ): Unit
    revise(
      w.otherHolder,
      0.43,
      Seq(example.barrierGone -> Some(0.4)),
      Some(w.notes("other-revision")),
      "13 May"
    ): Unit
  }

  Then("the claim is given with both weights") { () =>
    val both = answered("both").arr
    assertEquals(both.map(_("support")("claim")("id").str).toList, List(example.barrierGone))
    assertEquals(both.head("weightA").num, 0.9)
    assertEquals(both.head("weightB").num, 0.4)
  }

  Given("the current belief revision of {string} rests on no claims") { (name: String) =>
    val belief =
      http.get(s"/beliefs/current?holder=${holderId(name)}&hypothesis=${example.yes}").json
    assertEquals(belief("current")("restsOn").arr.size, 0)
  }

  Then("{string} is shown as stating no reasons") { (name: String) =>
    val side = Seq(answered("a"), answered("b")).find(_("holder").str == holderId(name)).get
    assertEquals(side("statesNoReasons").bool, true)
    val other = Seq(answered("a"), answered("b")).find(_("holder").str != holderId(name)).get
    assertEquals(other("statesNoReasons").bool, false)
  }

  Given("the holder {string} with a belief in {string}") { (name: String, hypothesis: String) =>
    w.notes("other-hypothesis") = w.hypotheses(hypothesis)
    revise(holderId(name), 0.5, Seq.empty, None, "12 May", w.hypotheses(hypothesis)): Unit
  }

  When("a reader compares the belief of {string} in {string} with that belief") {
    (a: String, hypothesis: String) =>
      compare(
        holderId(a),
        w.otherHolder,
        w.hypotheses(hypothesis),
        Some(w.notes("other-hypothesis"))
      )
  }

  Then("the reader is refused, naming the two hypotheses") { () =>
    assertEquals(w.last.status, 422, w.last.body)
    assertEquals(w.last.rule, "answer.beliefs.in-two-hypotheses")
    assertEquals(w.last.names.values.toSet, Set(example.yes, example.no))
  }
