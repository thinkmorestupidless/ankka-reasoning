package reasoning.steps

import com.thinkmorestupidless.ankka.testkit.GherkinSuite
import reasoning.belief.application.SettableClock
import reasoning.belief.domain.{Evidence, Moment}
import reasoning.support.{Answer, Eventually, Http, ServiceFixture}
import ujson.{Arr, Obj}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt

/**
 * One living feature, run as tests against the service over HTTP: each scenario of the file is a
 * test, each step a request a writer or a reader would make.
 *
 * The steps here are those of `features/record/`. Every scenario works under identifiers of its
 * own, so the suites share one running service without seeing each other's records. A refusal is
 * checked by its rule and by what it names, since a status alone passes for the wrong refusal.
 */
abstract class FeatureSuite(feature: String)
    extends GherkinSuite(FeatureSuite.directoryOf(feature)):

  override val munitTimeout = 5.minutes

  /** The service the steps speak to, and its clock. A graph suite overrides both. */
  protected def http: Http           = ServiceFixture.http
  protected def clock: SettableClock = ServiceFixture.clock
  protected def clockStart: Moment   = ServiceFixture.Start

  protected var w: World = World("unset")

  override def beforeEach(context: BeforeEach): Unit =
    clock.set(clockStart)
    w = World(s"${FeatureSuite.tag(feature)}${FeatureSuite.next()}")

  // ── Helpers ───────────────────────────────────────────────────────────────

  protected val Yes = "it launches this year"
  protected val No  = "it does not launch this year"

  protected def ok(answer: Answer): Answer =
    assert(
      answer.status == 200 || answer.status == 201,
      s"expected the record to be held: ${answer.body}"
    )
    answer

  /** Sends a write, remembering it so a later step can send the same again. */
  protected def write(path: String, body: ujson.Value, writer: String = Http.Writer): Answer =
    w.sentPath = path
    w.sentBody = body
    w.last = http.post(path, body, writer)
    w.firstAnswer = w.last
    w.last

  protected def refusedBy(rule: String): Unit =
    assert(w.last.status >= 400, s"expected a refusal, got ${w.last.status}: ${w.last.body}")
    assertEquals(w.last.rule, rule, w.last.body)

  protected def held(path: String): ujson.Value =
    val answer = http.get(path)
    assertEquals(answer.status, 200, answer.body)
    answer.json

  protected def notHeld(path: String): Unit =
    assertEquals(http.get(path).status, 404, s"expected nothing to be held at $path")

  protected def questionBody(
      id: String,
      statement: String,
      hypotheses: Seq[(String, String)]
  ): Obj =
    Obj(
      "id"        -> id,
      "statement" -> statement,
      "hypotheses" -> Arr.from(
        hypotheses.map((hid, text) => Obj("id" -> hid, "statement" -> text))
      ),
      "dated" -> World.date("1 May")
    )

  /** A question with the two hypotheses of the launch example, known by their statements. */
  protected def openQuestion(yesStatement: String = Yes): String =
    val id = w.fresh("q")
    ok(
      write(
        "/questions",
        questionBody(
          id,
          "Will Company X launch Product Y this year?",
          Seq("yes" -> yesStatement, "no" -> No)
        )
      )
    )
    w.question = id
    w.hypotheses(yesStatement) = s"$id/yes"
    w.hypotheses(No) = s"$id/no"
    w.hypothesis = s"$id/yes"
    id

  protected def registerHolder(
      kind: String = "agent",
      writer: String = Http.Writer,
      name: String = "a holder"
  ): String =
    val id = w.fresh("h")
    ok(write("/holders", Obj("id" -> id, "kind" -> kind, "name" -> name), writer))
    id

  protected def registerSource(name: String = "a source"): String =
    val id = w.fresh("src")
    ok(write("/sources", Obj("id" -> id, "name" -> name)))
    w.source = id
    id

  protected def evidenceBody(source: String, day: String): Obj =
    Obj(
      "source"     -> source,
      "locator"    -> s"https://example.test/${w.fresh("doc")}",
      "excerpt"    -> s"what was observed on $day",
      "observedAt" -> World.date(day)
    )

  /** Evidence from a source of its own, observed on a day. */
  protected def recordEvidence(day: String = "4 May"): String =
    val source = if w.source.isEmpty then registerSource() else w.source
    val id     = ok(write("/evidence", evidenceBody(source, day))).json("id").str
    w.evidence = id
    id

  protected def claimBody(
      id: String,
      holder: String,
      day: String,
      evidence: Seq[String],
      stances: Seq[(String, String)],
      revises: Option[String] = None,
      statement: String = "a statement"
  ): Obj =
    val body = Obj(
      "id"          -> id,
      "holder"      -> holder,
      "statement"   -> statement,
      "derivesFrom" -> Arr.from(evidence),
      "stances"     -> Arr.from(stances.map((h, s) => Obj("hypothesis" -> h, "stance" -> s))),
      "dated"       -> World.date(day)
    )
    revises.foreach(r => body("revises") = r)
    body

  /**
   * A claim by the scenario's holder, derived from the scenario's evidence, supporting its
   * hypothesis.
   */
  protected def stateClaim(
      day: String = "4 May",
      revises: Option[String] = None,
      holder: String = "",
      writer: String = Http.Writer
  ): String =
    val id = w.fresh("c")
    val by = if holder.isEmpty then w.holder else holder
    ok(
      write(
        "/claims",
        claimBody(id, by, day, Seq(w.evidence), Seq(w.hypothesis -> "supports"), revises),
        writer
      )
    )
    w.claims = w.claims :+ id
    id

  protected def beliefBody(
      id: String,
      holder: String,
      probability: Double,
      restsOn: Seq[(String, Option[Double])],
      follows: Option[String],
      day: Option[String]
  ): Obj =
    val body = Obj(
      "id"          -> id,
      "holder"      -> holder,
      "hypothesis"  -> w.hypothesis,
      "probability" -> probability,
      "restsOn" -> Arr.from(restsOn.map { (claim, weight) =>
        val rest = Obj("claim" -> claim)
        weight.foreach(x => rest("weight") = x)
        rest
      })
    )
    follows.foreach(f => body("follows") = f)
    day.foreach(d => body("dated") = World.date(d))
    body

  protected def stateBelief(
      probability: Double,
      restsOn: Seq[(String, Option[Double])] = Seq.empty,
      follows: Option[String] = None,
      day: Option[String] = Some("4 May"),
      holder: String = "",
      writer: String = Http.Writer
  ): Answer =
    val id = w.fresh("r")
    val by = if holder.isEmpty then w.holder else holder
    val answer =
      write("/beliefs/revisions", beliefBody(id, by, probability, restsOn, follows, day), writer)
    if answer.status == 201 then
      w.revision = id
      w.revisions = w.revisions :+ id
    answer

  protected def belief(holder: String = ""): ujson.Value =
    val by = if holder.isEmpty then w.holder else holder
    held(s"/beliefs/current?holder=$by&hypothesis=${w.hypothesis}")

  protected def line(holder: String = ""): Vector[ujson.Value] =
    val by = if holder.isEmpty then w.holder else holder
    held(s"/beliefs/line?holder=$by&hypothesis=${w.hypothesis}")("revisions").arr.toVector

  protected def revisedBy(claim: String): List[String] =
    held(s"/claims/$claim")("revisedBy").arr.map(_.str).toList

  // ── Any refusal ───────────────────────────────────────────────────────────

  Then("the writer is refused") { () =>
    val all = if w.answers.nonEmpty then w.answers else Vector(w.last)
    all.foreach(a => assert(a.status >= 400, s"expected a refusal, got ${a.status}: ${a.body}"))
  }

  Then("the writer is refused, naming the rule") { () =>
    assert(
      w.breaks.nonEmpty,
      "the step that was refused did not say which rule it set out to break"
    )
    refusedBy(w.breaks)
  }

  Then("the writer is refused, naming what is not held") { () =>
    refusedBy(w.breaks)
    assert(w.breaks.endsWith("not-held"), w.breaks)
    assert(
      w.last.names.values.exists(_.contains("missing")),
      s"the refusal does not name what is not held: ${w.last.body}"
    )
  }

  Then("the writer is refused, naming the holder") { () =>
    refusedBy("holder.writer.does-not-speak")
    assertEquals(w.last.status, 403)
    assertEquals(w.last.names.get("holder"), Some(w.holder), w.last.body)
  }

  // ── Questions ─────────────────────────────────────────────────────────────

  Given("a question with two hypotheses")(() => openQuestion(): Unit)
  Given("a question a writer opened")(() => openQuestion(): Unit)
  Given("a question with no market")(() => openQuestion(): Unit)
  Given("a question with the hypothesis {string}")((statement: String) =>
    openQuestion(statement): Unit
  )

  When("a writer opens the question {string} with the hypotheses {string} and {string}") {
    (statement: String, first: String, second: String) =>
      val id = w.fresh("q")
      w.question = id
      write("/questions", questionBody(id, statement, Seq("yes" -> first, "no" -> second))): Unit
      w.hypotheses(first) = s"$id/yes"
      w.hypotheses(second) = s"$id/no"
  }

  Then("the question is held with both hypotheses") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    val question = held(s"/questions/${w.question}")
    assertEquals(
      question("hypotheses").arr.map(_("statement").str).toList,
      w.hypotheses.keys.toList
    )
  }

  When("a writer opens a question with one hypothesis") { () =>
    w.question = w.fresh("q")
    w.breaks = "question.hypotheses.at-least-two"
    write(
      "/questions",
      questionBody(w.question, "a question", Seq("only" -> "the only answer"))
    ): Unit
  }

  Then("no question is held")(() => notHeld(s"/questions/${w.question}"))

  When("a writer adds the hypothesis {string}") { (statement: String) =>
    write(
      s"/questions/${w.question}/hypotheses",
      Obj("id" -> "third", "statement" -> statement, "dated" -> World.date("2 May"))
    ): Unit
  }

  Then("the question has three hypotheses") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(held(s"/questions/${w.question}")("hypotheses").arr.size, 3)
  }

  Then("the two earlier hypotheses read as they were stated") { () =>
    val statements =
      held(s"/questions/${w.question}")("hypotheses").arr.map(_("statement").str).toList
    assertEquals(statements.take(2), List(Yes, No))
  }

  // There is no route that changes a record. Asking is sending it again in other words, which is
  // a different record under an identifier that is taken.
  When("a writer asks to change the statement of the question or of a hypothesis") { () =>
    val reworded = questionBody(w.question, "Will it, though?", Seq("yes" -> Yes, "no" -> No))
    val hypothesis =
      Obj("id" -> "yes", "statement" -> "it launches eventually", "dated" -> World.date("2 May"))
    w.answers = Vector(
      http.post("/questions", reworded),
      http.post(s"/questions/${w.question}/hypotheses", hypothesis)
    )
    w.answers.foreach(a => assertEquals(a.rule, "id.held-by-another", a.body))
  }

  Then("each reads as it was stated") { () =>
    val question = held(s"/questions/${w.question}")
    assertEquals(question("statement").str, "Will Company X launch Product Y this year?")
    assertEquals(question("hypotheses").arr.map(_("statement").str).toList, List(Yes, No))
  }

  When("the writer sends the same question again")(() => w.last = http.post(w.sentPath, w.sentBody))

  Then("one question is held, as it was first opened") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json, w.firstAnswer.json)
  }

  When("a writer records evidence, a claim and a belief about it") { () =>
    w.holder = registerHolder()
    val evidence = recordEvidence()
    val claim    = stateClaim()
    val stated   = stateBelief(0.5, Seq(claim -> None))
    assertEquals(stated.status, 201, stated.body)
    w.notes("evidence") = evidence
    w.claim = claim
  }

  Then("each is held as it is for any question") { () =>
    assertEquals(held(s"/evidence/${w.notes("evidence")}")("id").str, w.notes("evidence"))
    assertEquals(held(s"/claims/${w.claim}")("claim")("holder").str, w.holder)
    assertEquals(belief()("count").num, 1.0)
  }

  // ── Holders and sources ───────────────────────────────────────────────────

  When("a writer registers a holder of kind {string}") { (kind: String) =>
    w.holder = w.fresh("h")
    w.notes("kind") = kind
    write("/holders", Obj("id" -> w.holder, "kind" -> kind, "name" -> "a holder")): Unit
  }

  Then("the holder is held with that kind") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(held(s"/holders/${w.holder}")("kind").str, w.notes("kind"))
  }

  Then("the writer is refused, naming the kind") { () =>
    refusedBy("holder.kind.not-in-vocabulary")
    assertEquals(w.last.names.get("kind"), Some(w.notes("kind")))
  }

  Then("no holder is held")(() => notHeld(s"/holders/${w.holder}"))

  When("a writer registers the source {string}") { (name: String) =>
    w.source = w.fresh("src")
    w.notes("name") = name
    write("/sources", Obj("id" -> w.source, "name" -> name)): Unit
  }

  Then("the source is held with that name") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(held(s"/sources/${w.source}")("name").str, w.notes("name"))
  }

  Given("a holder a writer registered")(() => w.holder = registerHolder())

  When("the writer sends the same holder again")(() => w.last = http.post(w.sentPath, w.sentBody))

  Then("one holder is held, as it was first registered") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json, w.firstAnswer.json)
  }

  When("a writer registers a holder") { () =>
    w.thatWriter = Http.Writer
    w.holder = registerHolder()
  }

  Then("that writer speaks for the holder") { () =>
    val writers = held(s"/holders/${w.holder}")("writers").arr.map(_.str).toList
    assertEquals(writers, List(Http.writer(w.thatWriter)))
  }

  Given("a holder a writer speaks for") { () =>
    w.thatWriter = "owner"
    w.holder = registerHolder(writer = "owner")
  }

  Given("a holder a writer does not speak for") { () =>
    w.holder = registerHolder(writer = "owner")
    w.thatWriter = "stranger"
  }

  When("that writer adds another writer to speak for the holder") { () =>
    w.last = http.post(
      s"/holders/${w.holder}/writers",
      Obj("writer" -> Http.writer("another")),
      w.thatWriter
    )
  }

  Then("both writers speak for the holder") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    val writers = held(s"/holders/${w.holder}")("writers").arr.map(_.str).toList
    assertEquals(writers, List(Http.writer("owner"), Http.writer("another")))
  }

  When("a writer records evidence") { () =>
    w.thatWriter = Http.Writer
    recordEvidence(): Unit
  }

  Then("the evidence is held with that writer") { () =>
    assertEquals(held(s"/evidence/${w.evidence}")("writer").str, Http.writer(w.thatWriter))
  }

  // ── Evidence ──────────────────────────────────────────────────────────────

  Given("the source {string}")((name: String) => registerSource(name): Unit)

  When(
    "a writer records evidence from the source with a locator, an author, the time it was published, the time it was observed and an excerpt"
  ) { () =>
    val body = Obj(
      "source"      -> w.source,
      "locator"     -> s"https://filings.example/${w.p}/q1",
      "excerpt"     -> "Regulatory approval remains pending.",
      "author"      -> "Company X",
      "publishedAt" -> World.date("3 May"),
      "observedAt"  -> World.date("4 May")
    )
    w.evidence = ok(write("/evidence", body)).json("id").str
  }

  Then("the evidence is held with each of them") { () =>
    val evidence = held(s"/evidence/${w.evidence}")
    assertEquals(evidence("source").str, w.source)
    assertEquals(evidence("locator").str, w.sentBody("locator").str)
    assertEquals(evidence("excerpt").str, w.sentBody("excerpt").str)
    assertEquals(evidence("author").str, "Company X")
    assertEquals(evidence("publishedAt").str, World.date("3 May"))
  }

  Then("the evidence is dated the time it was observed") { () =>
    assertEquals(held(s"/evidence/${w.evidence}")("dated").str, World.date("4 May"))
  }

  Then("the evidence is held with the time it was recorded") { () =>
    assertEquals(held(s"/evidence/${w.evidence}")("recordedAt").str, clock.now().iso)
  }

  When("a writer records evidence from a source that is not registered") { () =>
    val body = evidenceBody(s"${w.p}-missing", "4 May")
    w.notes("digest") =
      Evidence.digest(body("source").str, body("locator").str, body("excerpt").str)
    write("/evidence", body): Unit
  }

  Then("the writer is refused, naming the source") { () =>
    refusedBy("evidence.source.not-held")
    assertEquals(w.last.names.get("source"), Some(s"${w.p}-missing"))
  }

  Then("no evidence is held")(() => notHeld(s"/evidence/${w.notes("digest")}"))

  Given("evidence recorded from the source with a locator and an excerpt")(() =>
    recordEvidence(): Unit
  )

  When(
    "a writer records evidence from the same source with the same locator and the same excerpt"
  ) { () =>
    val again = ujson.copy(w.sentBody)
    again("author") = "someone else"
    again("observedAt") = World.date("6 May")
    w.last = http.post("/evidence", again, "another")
  }

  Then("one piece of evidence is held") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json("id").str, w.evidence)
  }

  Then("the writer is given the evidence first recorded")(() =>
    assertEquals(w.last.json, w.firstAnswer.json)
  )

  Given("evidence")(() => recordEvidence(): Unit)

  // There is no route to change evidence by: its identifier is what it says, so other words are
  // other evidence. Asking is a request the service has no way to accept.
  When("a writer asks to change its excerpt") { () =>
    w.answers = Vector(
      http.send(
        "PUT",
        s"/evidence/${w.evidence}",
        Some("""{"excerpt":"other words"}"""),
        Http.Writer
      ),
      http.send(
        "PATCH",
        s"/evidence/${w.evidence}",
        Some("""{"excerpt":"other words"}"""),
        Http.Writer
      )
    )
  }

  Then("the evidence reads as it was recorded") { () =>
    assertEquals(held(s"/evidence/${w.evidence}"), w.firstAnswer.json)
  }

  When("a writer records evidence observed on {string}, and today is {string}") {
    (observed: String, today: String) =>
      clock.set(Moment.parse(World.noon(today)).get)
      recordEvidence(observed): Unit
  }

  Then("the evidence is dated {string}") { (day: String) =>
    assertEquals(held(s"/evidence/${w.evidence}")("dated").str, World.date(day))
  }

  Then("the evidence is held with the time it was recorded, {string}") { (day: String) =>
    assertEquals(
      held(s"/evidence/${w.evidence}")("recordedAt").str,
      Moment.parse(World.noon(day)).get.iso
    )
  }

  When("a writer records evidence observed at a time later than now") { () =>
    w.breaks = "dated.later-than-now"
    val body = evidenceBody(w.source, "4 May")
    body("observedAt") = Moment(clock.now().millis + 60000).iso
    write("/evidence", body): Unit
  }

  When("a writer records evidence published on {string} and observed on {string}") {
    (published: String, observed: String) =>
      w.breaks = "evidence.observed-before-published"
      val body = evidenceBody(w.source, observed)
      body("publishedAt") = World.date(published)
      write("/evidence", body): Unit
  }

  When("a writer records evidence with an excerpt longer than the limit") { () =>
    val body = evidenceBody(w.source, "4 May")
    body("excerpt") = "x" * (ServiceFixture.settings.excerptLimit + 1)
    write("/evidence", body): Unit
  }

  Then("the writer is refused, naming the limit") { () =>
    refusedBy("text.length")
    assertEquals(w.last.names.get("limit"), Some(ServiceFixture.settings.excerptLimit.toString))
    assertEquals(w.last.names.get("field"), Some("excerpt"))
  }

  // ── Claims ────────────────────────────────────────────────────────────────

  Given("a holder")(() => w.holder = registerHolder())
  Given("another holder")(() => w.otherHolder = registerHolder())
  Given("the holder {string}")((name: String) => w.holder = registerHolder(name = name))
  Given("evidence dated {string}")((day: String) => recordEvidence(day): Unit)

  When(
    "the holder states the claim {string}, derived from the evidence, which contradicts the hypothesis {string}"
  ) { (statement: String, hypothesis: String) =>
    w.claim = w.fresh("c")
    w.notes("statement") = statement
    w.notes("hypothesis") = w.hypotheses(hypothesis)
    write(
      "/claims",
      claimBody(
        w.claim,
        w.holder,
        "4 May",
        Seq(w.evidence),
        Seq(w.hypotheses(hypothesis) -> "contradicts"),
        statement = statement
      )
    ): Unit
  }

  Then(
    "the claim is held with its statement, its holder, the evidence it derives from and its stance on the hypothesis"
  ) { () =>
    assertEquals(w.last.status, 201, w.last.body)
    val claim = held(s"/claims/${w.claim}")("claim")
    assertEquals(claim("statement").str, w.notes("statement"))
    assertEquals(claim("holder").str, w.holder)
    assertEquals(claim("derivesFrom").arr.map(_.str).toList, List(w.evidence))
    assertEquals(
      claim("stances").arr.map(s => s("hypothesis").str -> s("stance").str).toList,
      List(w.notes("hypothesis") -> "contradicts")
    )
  }

  When("the holder states a claim that supports one hypothesis and contradicts the other") { () =>
    w.claim = w.fresh("c")
    val stances = Seq(w.hypotheses(Yes) -> "supports", w.hypotheses(No) -> "contradicts")
    write("/claims", claimBody(w.claim, w.holder, "4 May", Seq(w.evidence), stances)): Unit
  }

  Then("the claim is held with both stances") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    val stances = held(s"/claims/${w.claim}")("claim")("stances").arr.map(_("stance").str).toList
    assertEquals(stances, List("supports", "contradicts"))
  }

  When("the holder states a claim derived from no evidence") { () =>
    w.breaks = "claim.derives-from.none"
    w.claim = w.fresh("c")
    write(
      "/claims",
      claimBody(w.claim, w.holder, "4 May", Seq.empty, Seq(w.hypothesis -> "supports"))
    ): Unit
  }

  When("the holder states a claim with a stance on no hypothesis") { () =>
    w.breaks = "claim.stance.none"
    w.claim = w.fresh("c")
    write("/claims", claimBody(w.claim, w.holder, "4 May", Seq(w.evidence), Seq.empty)): Unit
  }

  When("the holder states a claim that names {} that is not held") { (something: String) =>
    w.claim = w.fresh("c")
    val missing  = s"${w.p}-missing"
    val evidence = Seq(w.evidence)
    val stance   = Seq(w.hypothesis -> "supports")
    val (rule, body) = something match
      case "a piece of evidence" =>
        "claim.derives-from.not-held" -> claimBody(
          w.claim,
          w.holder,
          "4 May",
          Seq("0" * 57 + "missing"),
          stance
        )
      case "a hypothesis" =>
        "claim.stance.hypothesis-not-held" -> claimBody(
          w.claim,
          w.holder,
          "4 May",
          evidence,
          Seq(s"${w.question}/missing" -> "supports")
        )
      case "a holder" =>
        "claim.holder.not-held" -> claimBody(w.claim, missing, "4 May", evidence, stance)
      case "a claim it revises" =>
        "claim.revises.not-held" -> claimBody(
          w.claim,
          w.holder,
          "4 May",
          evidence,
          stance,
          Some(missing)
        )
    w.breaks = rule
    write("/claims", body): Unit
  }

  Then("no claim is held")(() => notHeld(s"/claims/${w.claim}"))

  When("the holder states a claim dated {string}, derived from the evidence") { (day: String) =>
    w.breaks = "claim.dated.not-before-evidence"
    w.claim = w.fresh("c")
    write(
      "/claims",
      claimBody(w.claim, w.holder, day, Seq(w.evidence), Seq(w.hypothesis -> "supports"))
    ): Unit
  }

  Given("a claim")(() => w.claim = stateClaim())
  Given("a claim the holder stated")(() => w.claim = stateClaim())
  Given("a claim dated {string}")((day: String) => w.claim = stateClaim(day))

  When("the holder states a claim dated {string} that revises it") { (day: String) =>
    w.breaks = "claim.dated.not-before-revised"
    w.laterClaim = w.fresh("c")
    write(
      "/claims",
      claimBody(
        w.laterClaim,
        w.holder,
        day,
        Seq(w.evidence),
        Seq(w.hypothesis -> "supports"),
        Some(w.claim)
      )
    ): Unit
  }

  Then("both claims are held") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(held(s"/claims/${w.laterClaim}")("claim")("revises").str, w.claim)
    assertEquals(held(s"/claims/${w.claim}")("claim")("id").str, w.claim)
  }

  Then("the earlier claim reads as it was stated") { () =>
    val claim = held(s"/claims/${w.claim}")("claim")
    assertEquals(claim("statement").str, "a statement")
    assertEquals(claim.obj.get("revises"), None)
  }

  Then("the earlier claim is a revised claim") { () =>
    Eventually.eventually()(assertEquals(revisedBy(w.claim), List(w.laterClaim)))
  }

  When("each holder states a claim that revises it") { () =>
    val first  = w.fresh("c")
    val second = w.fresh("c")
    w.answers = Vector(
      http.post(
        "/claims",
        claimBody(
          first,
          w.holder,
          "5 May",
          Seq(w.evidence),
          Seq(w.hypothesis -> "supports"),
          Some(w.claim)
        )
      ),
      http.post(
        "/claims",
        claimBody(
          second,
          w.otherHolder,
          "6 May",
          Seq(w.evidence),
          Seq(w.hypothesis -> "contradicts"),
          Some(w.claim)
        )
      )
    )
    w.claims = Vector(w.claim, first, second)
  }

  Then("three claims are held") { () =>
    w.answers.foreach(a => assertEquals(a.status, 201, a.body))
    w.claims.foreach(id => assertEquals(held(s"/claims/$id")("claim")("id").str, id))
  }

  Then("the earlier claim is revised by both later claims") { () =>
    Eventually.eventually()(assertEquals(revisedBy(w.claim), w.claims.drop(1).toList))
  }

  When("a writer asks to change its statement, its evidence or its stance") { () =>
    val other = recordEvidence("4 May")
    val base = (statement: String, evidence: String, stance: String) =>
      claimBody(
        w.claim,
        w.holder,
        "4 May",
        Seq(evidence),
        Seq(w.hypothesis -> stance),
        statement = statement
      )
    val original = held(s"/claims/${w.claim}")("claim")("derivesFrom").arr.head.str
    w.answers = Vector(
      http.post("/claims", base("other words", original, "supports")),
      http.post("/claims", base("a statement", other, "supports")),
      http.post("/claims", base("a statement", original, "contradicts"))
    )
    w.answers.foreach(a => assertEquals(a.rule, "id.held-by-another", a.body))
    w.evidence = original
  }

  Then("the claim reads as it was stated") { () =>
    val claim = held(s"/claims/${w.claim}")("claim")
    assertEquals(claim("statement").str, "a statement")
    assertEquals(claim("derivesFrom").arr.map(_.str).toList, List(w.evidence))
    assertEquals(claim("stances").arr.map(_("stance").str).toList, List("supports"))
  }

  When("the writer sends the same claim again")(() => w.last = http.post(w.sentPath, w.sentBody))

  Then("one claim is held, as it was first stated") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json, w.firstAnswer.json)
  }

  Given("a writer who does not speak for the holder")(() => w.thatWriter = "stranger")

  When("that writer states a claim as the holder") { () =>
    w.claim = w.fresh("c")
    write(
      "/claims",
      claimBody(w.claim, w.holder, "4 May", Seq(w.evidence), Seq(w.hypothesis -> "supports")),
      w.thatWriter
    ): Unit
  }

  // ── Beliefs ───────────────────────────────────────────────────────────────

  Given("a claim dated {string} with a stance on the hypothesis") { (day: String) =>
    recordEvidence(day): Unit
    w.claim = stateClaim(day)
  }

  Given("a later claim dated {string}") { (day: String) =>
    recordEvidence(day): Unit
    w.laterClaim = stateClaim(day)
  }

  When(
    "the holder states a belief of {string} in the hypothesis, resting on the claim, dated {string}"
  ) { (probability: String, day: String) =>
    w.notes("probability") = probability
    w.notes("day") = day
    stateBelief(probability.toDouble, Seq(w.claim -> None), day = Some(day)): Unit
  }

  Then("the belief has one belief revision")(() => assertEquals(belief()("count").num, 1.0))

  Then("the belief revision is held with its probability, the claim it rests on and its date") {
    () =>
      assertEquals(w.last.status, 201, w.last.body)
      val revision = held(s"/beliefs/revisions/${w.revision}")
      assertEquals(revision("probability").num, w.notes("probability").toDouble)
      assertEquals(revision("restsOn").arr.map(_("claim").str).toList, List(w.claim))
      assertEquals(revision("dated").str, World.date(w.notes("day")))
      assertEquals(revision("n").num, 1.0)
  }

  Given("the holder's belief of {string} in the hypothesis") { (probability: String) =>
    ok(stateBelief(probability.toDouble, Seq(w.claim -> None))): Unit
  }

  When("the holder revises the belief to {string}, resting on the later claim, dated {string}") {
    (probability: String, day: String) =>
      stateBelief(
        probability.toDouble,
        Seq(w.laterClaim -> None),
        follows = Some(w.revision),
        day = Some(day)
      ): Unit
  }

  Then("the belief has two belief revisions, the second following the first") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(belief()("count").num, 2.0)
    val revisions = line()
    assertEquals(revisions.map(_("id").str), w.revisions.reverse)
    assertEquals(revisions.head("follows").str, w.revisions.head)
    assertEquals(revisions.map(_("n").num), Vector(2.0, 1.0))
  }

  Then("the current belief revision has the probability {string}") { (probability: String) =>
    assertEquals(belief()("current")("probability").num, probability.toDouble)
  }

  Then("the first belief revision reads as it was stated") { () =>
    val first = held(s"/beliefs/revisions/${w.revisions.head}")
    assertEquals(first("probability").num, 0.38)
    assertEquals(first("restsOn").arr.map(_("claim").str).toList, List(w.claim))
    assertEquals(first.obj.get("follows"), None)
  }

  When("the holder states a belief of {string} in the hypothesis") { (probability: String) =>
    w.breaks = "belief.probability.range"
    stateBelief(probability.toDouble): Unit
  }

  When("the holder states a belief of {string} in the hypothesis, resting on no claims") {
    (probability: String) =>
      stateBelief(probability.toDouble): Unit
  }

  Then("the belief revision is held, resting on no claims") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(held(s"/beliefs/revisions/${w.revision}")("restsOn").arr.size, 0)
  }

  When("the holder states a belief resting on the claim with the weight {string}") {
    (weight: String) =>
      w.notes("weight") = weight
      stateBelief(0.5, Seq(w.claim -> Some(weight.toDouble))): Unit
  }

  Then("the belief revision is held with that weight for that claim") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    val rest = held(s"/beliefs/revisions/${w.revision}")("restsOn").arr.head
    assertEquals(rest("claim").str, w.claim)
    assertEquals(rest("weight").num, w.notes("weight").toDouble)
  }

  Given("a claim with a stance on a hypothesis of another question") { () =>
    val question = w.question
    val yes      = w.hypothesis
    val other    = openQuestion()
    w.foreignClaim = stateClaim()
    w.otherQuestion = other
    w.question = question
    w.hypothesis = yes
    w.hypotheses(Yes) = yes
  }

  When("the holder states a belief in the hypothesis resting on that claim") { () =>
    stateBelief(0.5, Seq(w.foreignClaim -> None)): Unit
  }

  Then("the writer is refused, naming the claim") { () =>
    refusedBy("belief.rests-on.other-question")
    assertEquals(w.last.names.get("claim"), Some(w.foreignClaim))
  }

  When("the holder states a belief dated {string} resting on the later claim") { (day: String) =>
    w.breaks = "belief.dated.not-before-claim"
    stateBelief(0.5, Seq(w.laterClaim -> None), day = Some(day)): Unit
  }

  Given("the holder's belief with a belief revision dated {string}") { (day: String) =>
    ok(stateBelief(0.5, day = Some(day))): Unit
  }

  When("the holder revises the belief with a belief revision dated {string}") { (day: String) =>
    w.breaks = "belief.dated.not-before-current"
    stateBelief(0.6, follows = Some(w.revision), day = Some(day)): Unit
  }

  When("the holder states a belief resting on a claim that is not held") { () =>
    w.breaks = "belief.rests-on.not-held"
    stateBelief(0.5, Seq(s"${w.p}-missing" -> None)): Unit
  }

  Then("the belief has no belief revision")(() => assertEquals(belief()("count").num, 0.0))

  Given("the holder's belief with one belief revision")(() => ok(stateBelief(0.5)): Unit)

  When("two writers each send a belief revision following that one") { () =>
    ok(http.post(s"/holders/${w.holder}/writers", Obj("writer" -> Http.writer("second")))): Unit
    val first = w.revision
    val bodies = Vector(Http.Writer, "second").map { writer =>
      writer -> beliefBody(w.fresh("r"), w.holder, 0.6, Seq.empty, Some(first), Some("5 May"))
    }
    val threads = bodies.map { (writer, body) =>
      val answer = new java.util.concurrent.atomic.AtomicReference[Answer]()
      val thread =
        Thread.ofVirtual().start(() => answer.set(http.post("/beliefs/revisions", body, writer)))
      (thread, answer)
    }
    threads.foreach(_._1.join())
    w.answers = threads.map(_._2.get())
  }

  Then("one is held and the other writer is refused, naming the current belief revision") { () =>
    val (accepted, refused) = w.answers.partition(_.status == 201)
    assertEquals(accepted.size, 1, w.answers.map(_.body).mkString("\n"))
    assertEquals(refused.size, 1, w.answers.map(_.body).mkString("\n"))
    assertEquals(refused.head.rule, "belief.follows.not-current", refused.head.body)
    assertEquals(refused.head.status, 409)
    assertEquals(refused.head.names.get("current"), Some(accepted.head.json("id").str))
  }

  Then("the belief has two belief revisions")(() => assertEquals(belief()("count").num, 2.0))

  When("the writer sends the same belief revision again")(() =>
    w.last = http.post(w.sentPath, w.sentBody)
  )

  When("each holder states a belief in the hypothesis with a different probability") { () =>
    w.answers = Vector(stateBelief(0.61), stateBelief(0.43, holder = w.otherHolder))
  }

  Then("two beliefs are held, one for each holder") { () =>
    w.answers.foreach(a => assertEquals(a.status, 201, a.body))
    assertEquals(belief()("current")("probability").num, 0.61)
    assertEquals(belief(w.otherHolder)("current")("probability").num, 0.43)
  }

  Then("neither belief changes the other") { () =>
    assertEquals(belief()("count").num, 1.0)
    assertEquals(belief(w.otherHolder)("count").num, 1.0)
  }

  When("that writer states a belief as the holder")(() =>
    stateBelief(0.5, writer = w.thatWriter): Unit
  )

object FeatureSuite:

  private val counter = AtomicInteger(0)

  /**
   * A directory holding a copy of one feature file and nothing else.
   *
   * ankka-testkit 0.10.0's `GherkinSuite` runs every feature under a directory and cannot be given
   * one file; the release after it can. Until this build moves to it, each suite is handed a
   * directory of its own, so that a suite never meets the steps of a feature that is not built yet.
   */
  def directoryOf(feature: String): String =
    val source = java.nio.file.Paths.get(feature)
    val directory = java.nio.file.Paths
      .get("target", "feature-suites", source.getFileName.toString.stripSuffix(".feature"))
    val _ = java.nio.file.Files.createDirectories(directory)
    java.nio.file.Files.copy(
      source,
      directory.resolve(source.getFileName),
      java.nio.file.StandardCopyOption.REPLACE_EXISTING
    ): Unit
    directory.toString

  def next(): Int = counter.incrementAndGet()

  /** A few letters for a feature file, so a record's identifier says which suite made it. */
  def tag(feature: String): String =
    feature.split('/').last.stripSuffix(".feature").split('-').map(_.take(2)).mkString.take(6)
