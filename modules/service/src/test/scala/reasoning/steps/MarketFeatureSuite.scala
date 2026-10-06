package reasoning.steps

import reasoning.belief.domain.Moment
import reasoning.support.{Answer, Http}
import ujson.{Arr, Obj}

/**
 * A living feature of the market layer, and the vocabulary's own feature, which needs a market to
 * show that a layer adds to the one beneath it and changes nothing in it.
 */
abstract class MarketFeatureSuite(feature: String) extends ExplainFeatureSuite(feature):

  private val ClosesAt = "2026-12-31T00:00:00Z"

  // ── Helpers ───────────────────────────────────────────────────────────────

  protected def marketBody(
      id: String,
      venue: String,
      outcomes: Seq[(String, String)],
      question: String = ""
  ): Obj =
    Obj(
      "id"       -> id,
      "question" -> (if question.isEmpty then example.question else question),
      "venue"    -> venue,
      "outcomes" -> Arr.from(
        outcomes.map((outcome, hypothesis) => Obj("outcome" -> outcome, "hypothesis" -> hypothesis))
      ),
      "resolutionCriteria" -> "Resolves YES if Company X announces general availability of Product Y this year.",
      "closesAt" -> ClosesAt,
      "dated"    -> World.date("2 May")
    )

  protected def openMarket(
      venue: String = "Example Exchange",
      writer: String = Http.Writer
  ): String =
    val id = w.fresh("m")
    ok(
      write(
        "/markets",
        marketBody(id, venue, Seq("YES" -> example.yes, "NO" -> example.no)),
        writer
      )
    ): Unit
    w.market = id
    w.published = w.published :+ ("market" -> id)
    id

  protected def marketHolder(market: String = ""): String =
    s"market.${if market.isEmpty then w.market else market}"

  protected def observe(
      price: Double,
      outcome: String = "YES",
      writer: String = Http.Writer,
      observedAt: Option[String] = None
  ): Answer =
    val body = Obj("outcome" -> outcome, "price" -> price)
    observedAt.foreach(at => body("observedAt") = at)
    // A minute passes before each observation, so two of them are two observations.
    if observedAt.isEmpty then clock.set(Moment(clock.now().millis + 60000))
    w.last = http.post(s"/markets/${w.market}/price-observations", body, writer)
    if w.last.status == 201 then
      w.published = w.published :+ ("revision" -> w.last.json("revision")("id").str)
    w.last

  protected def marketBelief(hypothesis: String = ""): ujson.Value =
    held(s"/beliefs/current?holder=${marketHolder()}&hypothesis=${
        if hypothesis.isEmpty then example.yes else hypothesis
      }")

  protected def resolveBody(
      id: String,
      outcome: Option[String],
      evidence: Seq[String],
      day: String = "14 November",
      revises: Option[String] = None
  ): Obj =
    val body = Obj(
      "id"        -> id,
      "evidence"  -> Arr.from(evidence),
      "authority" -> "Company X announcement",
      "dated"     -> World.date(day)
    )
    outcome.foreach(o => body("outcome") = o)
    revises.foreach(r => body("revises") = r)
    body

  protected def resolve(
      outcome: Option[String],
      evidence: Seq[String],
      writer: String = Http.Writer,
      day: String = "14 November",
      revises: Option[String] = None
  ): Answer =
    val id = w.fresh("res")
    w.notes("resolution") = id
    write(
      s"/markets/${w.market}/resolutions",
      resolveBody(id, outcome, evidence, day, revises),
      writer
    )

  protected def resolutions: Vector[ujson.Value] =
    held(s"/markets/${w.market}")("resolutions").arr.toVector

  // ── Markets ───────────────────────────────────────────────────────────────

  When(
    // A step of testkit 0.10.0 takes at most four values, so the second outcome is read as written.
    "a writer opens a market about the question on the venue {string}, with the outcome {string} for {string} and the outcome \"NO\" for \"it does not launch this year\", its resolution criteria and its closing time"
  ) { (venue: String, first: String, firstHypothesis: String) =>
    val second           = "NO"
    val secondHypothesis = No
    w.market = w.fresh("m")
    w.thatWriter = Http.Writer
    w.notes("venue") = venue
    write(
      "/markets",
      marketBody(
        w.market,
        venue,
        Seq(first -> w.hypotheses(firstHypothesis), second -> w.hypotheses(secondHypothesis))
      )
    ): Unit
  }

  Then(
    "the market is held with its venue, its outcomes, its resolution criteria and its closing time"
  ) { () =>
    assertEquals(w.last.status, 201, w.last.body)
    val market = held(s"/markets/${w.market}")
    assertEquals(market("venue").str, w.notes("venue"))
    assertEquals(
      market("outcomes").arr.map(o => o("outcome").str -> o("hypothesis").str).toList,
      List("YES" -> example.yes, "NO" -> example.no)
    )
    assertEquals(market("resolutionCriteria").str, w.sentBody("resolutionCriteria").str)
    assertEquals(market("closesAt").str, ClosesAt)
    assertEquals(market("question").str, example.question)
  }

  Then("the market is registered as a holder of kind {string}") { (kind: String) =>
    val holder = held(s"/holders/${marketHolder()}")
    assertEquals(holder("kind").str, kind)
    assertEquals(held(s"/markets/${w.market}")("holder").str, marketHolder())
  }

  Then("that writer speaks for the market") { () =>
    assertEquals(
      held(s"/holders/${marketHolder()}")("writers").arr.map(_.str).toList,
      List(Http.writer(w.thatWriter))
    )
  }

  When(
    "a writer opens a market about the question with an outcome for a hypothesis of another question"
  ) { () =>
    val other = openQuestion()
    w.question = example.question
    w.market = w.fresh("m")
    w.notes("foreign") = s"$other/yes"
    write(
      "/markets",
      marketBody(w.market, "Example Exchange", Seq("YES" -> example.yes, "NO" -> s"$other/yes"))
    ): Unit
  }

  Then("the writer is refused, naming the hypothesis") { () =>
    refusedBy("market.outcome.hypothesis-of-other-question")
    assertEquals(w.last.names.get("hypothesis"), Some(w.notes("foreign")))
    notHeld(s"/markets/${w.market}")
  }

  Given("a market about the question")(() => openMarket(): Unit)

  When("a writer opens another market about the question on another venue") { () =>
    w.otherMarket = w.market
    openMarket("Another Exchange"): Unit
  }

  Then("two markets are held, each a holder of its own") { () =>
    val first  = held(s"/markets/${w.otherMarket}")
    val second = held(s"/markets/${w.market}")
    assertEquals(first("question").str, second("question").str)
    assertNotEquals(first("holder").str, second("holder").str)
    Seq(first, second).foreach(m =>
      assertEquals(held(s"/holders/${m("holder").str}")("kind").str, "market")
    )
  }

  When("a writer records a price observation of {string} for the outcome {string}") {
    (price: String, outcome: String) =>
      observe(price.toDouble, outcome): Unit
  }

  Then("the market's belief in {string} has a belief revision with the probability {string}") {
    (hypothesis: String, probability: String) =>
      assertEquals(w.last.status, 201, w.last.body)
      assertEquals(w.last.json("added").bool, true)
      val belief = marketBelief(w.hypotheses(hypothesis))
      assertEquals(belief("count").num, 1.0)
      assertEquals(belief("current")("probability").num, probability.toDouble)
  }

  Then("the belief revision rests on no claims") { () =>
    assertEquals(marketBelief()("current")("restsOn").arr.size, 0)
  }

  Given("a market whose current price observation for the outcome {string} is {string}") {
    (outcome: String, price: String) =>
      openMarket(): Unit
      assertEquals(observe(price.toDouble, outcome).status, 201, w.last.body)
      w.notes("count") = marketBelief()("count").num.toString
  }

  Then("the market's belief has the belief revisions it had") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json("added").bool, false)
    assertEquals(marketBelief()("count").num.toString, w.notes("count"))
  }

  Given("a writer who does not speak for the market")(() => w.thatWriter = "stranger")

  When("that writer records a price observation of {string} for the outcome {string}") {
    (price: String, outcome: String) =>
      observe(price.toDouble, outcome, writer = w.thatWriter): Unit
  }

  Then("the writer is refused, naming the market") { () =>
    refusedBy("holder.writer.does-not-speak")
    assertEquals(w.last.status, 403)
    assertEquals(w.last.names.get("market"), Some(w.market))
  }

  Then("the market's belief has no belief revision")(() =>
    assertEquals(marketBelief()("count").num, 0.0)
  )

  When("a writer records a price observation for the outcome {string}") { (outcome: String) =>
    w.notes("outcome") = outcome
    observe(0.5, outcome): Unit
  }

  Then("the writer is refused, naming the outcome") { () =>
    refusedBy("market.outcome.not-offered")
    assertEquals(w.last.names.get("outcome"), Some(w.notes("outcome")))
  }

  When("a writer records a price observation dated later than the closing time") { () =>
    // The service's clock is past the closing time, so the observation is not also in the future.
    clock.set(Moment.parse("2027-01-10T00:00:00Z").get)
    observe(0.5, observedAt = Some("2027-01-05T00:00:00Z")): Unit
  }

  Then("the writer is refused, naming the closing time") { () =>
    refusedBy("market.price.after-close")
    assertEquals(w.last.names.get("closesAt"), Some(ClosesAt))
  }

  When("a reader compares the beliefs of the market and {string} in {string}") {
    (name: String, hypothesis: String) =>
      ask(
        s"/answers/comparison?hypothesis=${w.hypotheses(hypothesis)}&a=${marketHolder()}&b=${holderId(name)}"
      ): Unit
  }

  Then("the claims {string} rests on") { (name: String) =>
    assertEquals(answered("b")("holder").str, holderId(name))
    assertEquals(claimsIn(answered("onlyB")), List(example.barrierGone))
    assertEquals(answered("onlyA").arr.size, 0)
    assertEquals(answered("both").arr.size, 0)
  }

  Then("the market is shown as stating no reasons") { () =>
    assertEquals(answered("a")("holder").str, marketHolder())
    assertEquals(answered("a")("statesNoReasons").bool, true)
    assertEquals(answered("b")("statesNoReasons").bool, false)
  }

  // ── Resolutions ───────────────────────────────────────────────────────────

  Given("a market about the question with the outcomes {string} and {string}") {
    (first: String, second: String) =>
      w.market = w.fresh("m")
      ok(
        write(
          "/markets",
          marketBody(w.market, "Example Exchange", Seq(first -> example.yes, second -> example.no))
        )
      ): Unit
      w.published = w.published :+ ("market" -> w.market)
      // What the market said the day before it was resolved.
      assertEquals(
        observe(0.9, first, observedAt = Some(World.date("13 November"))).status,
        201,
        w.last.body
      )
  }

  Given("evidence from the source {string} dated {string}") { (source: String, day: String) =>
    assertEquals(source, "Company X filings")
    w.evidence = ok(
      http.post(
        "/evidence",
        Obj(
          "source"     -> example.filings,
          "locator"    -> s"https://filings.example/${w.p}/launch",
          "excerpt"    -> "Product Y is generally available from today.",
          "observedAt" -> World.date(day)
        )
      )
    ).json("id").str
    w.published = w.published :+ ("evidence" -> w.evidence)
  }

  When(
    "a writer resolves the market to the outcome {string}, on that evidence, on the authority {string}, dated {string}"
  ) { (outcome: String, authority: String, day: String) =>
    w.notes("authority") = authority
    w.notes("day") = day
    resolve(Some(outcome), Seq(w.evidence), day = day): Unit
  }

  Then("the resolution is held with its outcome, its evidence, its authority and its date") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    val resolution = resolutions.last
    assertEquals(resolution("outcome").str, "YES")
    assertEquals(resolution("evidence").arr.map(_.str).toList, List(w.evidence))
    assertEquals(resolution("authority").str, w.notes("authority"))
    assertEquals(resolution("dated").str, World.date(w.notes("day")))
  }

  When("a writer resolves the market to the outcome {string} on no evidence") { (outcome: String) =>
    w.breaks = "market.resolution.evidence.none"
    resolve(Some(outcome), Seq.empty): Unit
  }

  Then("the market has no resolution")(() => assertEquals(resolutions.size, 0))

  When("a writer resolves the market as void, on that evidence")(() =>
    resolve(None, Seq(w.evidence)): Unit
  )

  Then("the resolution is held with no outcome") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(resolutions.last.obj.get("outcome"), None)
  }

  When("that writer resolves the market to the outcome {string}, on that evidence") {
    (outcome: String) =>
      resolve(Some(outcome), Seq(w.evidence), writer = w.thatWriter): Unit
  }

  When("a writer resolves the market to the outcome {string}, on that evidence") {
    (outcome: String) =>
      w.notes("outcome") = outcome
      resolve(Some(outcome), Seq(w.evidence)): Unit
  }

  Given("the market resolved to the outcome {string} on that evidence") { (outcome: String) =>
    ok(resolve(Some(outcome), Seq(w.evidence))): Unit
    w.notes("first-resolution") = w.notes("resolution")
  }

  When("a reader asks why the market was resolved")(() =>
    ask(s"/markets/${w.market}/resolution"): Unit
  )

  Then(
    "the reader is given the outcome {string}, the authority, the evidence and the source of that evidence"
  ) { (outcome: String) =>
    assertEquals(answered("resolution")("outcome").str, outcome)
    assertEquals(answered("resolution")("authority").str, "Company X announcement")
    assertEquals(answered("hypothesis").str, example.yes)
    assertEquals(answered("evidence").arr.map(_("id").str).toList, List(w.evidence))
    assertEquals(answered("evidence").arr.head("source")("name").str, "Company X filings")
  }

  When(
    "a writer resolves the market to the outcome {string}, on later evidence, revising the earlier resolution"
  ) { (outcome: String) =>
    val later = ok(
      http.post(
        "/evidence",
        Obj(
          "source"     -> example.regulator,
          "locator"    -> s"https://regulator.example/${w.p}/recall",
          "excerpt"    -> "The approval of Product Y is withdrawn.",
          "observedAt" -> World.date("20 November")
        )
      )
    ).json("id").str
    resolve(
      Some(outcome),
      Seq(later),
      day = "20 November",
      revises = Some(w.notes("first-resolution"))
    ): Unit
  }

  Then("both resolutions are held") { () =>
    assertEquals(w.last.status, 201, w.last.body)
    assertEquals(resolutions.map(_("outcome").str), Vector("YES", "NO"))
    assertEquals(resolutions.last("revises").str, w.notes("first-resolution"))
  }

  Then("the market's current resolution has the outcome {string}") { (outcome: String) =>
    caughtUp("market", w.market)
    assertEquals(ask(s"/markets/${w.market}/resolution").json("resolution")("outcome").str, outcome)
  }

  When("the writer sends the same resolution again")(() =>
    w.last = http.post(w.sentPath, w.sentBody)
  )

  Then("the market has one resolution") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(resolutions.size, 1)
  }

  When("a reader asks what each holder believed when the market was resolved") { () =>
    ask(s"/markets/${w.market}/beliefs-at-resolution"): Unit
  }

  Then(
    "the reader is given, for each holder, the last belief revision dated before the resolution for each hypothesis the market offers an outcome for"
  ) { () =>
    val beliefs =
      answered("beliefs").arr.map(b => b("holder").str -> b("revision")("probability").num).toMap
    assertEquals(beliefs, Map(example.holder -> 0.61, marketHolder() -> 0.9))
  }

  Then("each is given with whether its hypothesis is the one the market resolved to") { () =>
    answered("beliefs").arr.foreach { belief =>
      assertEquals(belief("hypothesis").str, example.yes)
      assertEquals(belief("resolvedTo").bool, true)
    }
    assertEquals(answered("hypothesis").str, example.yes)
  }

  // ── The vocabulary ────────────────────────────────────────────────────────

  private def layer(name: String): ujson.Value =
    answered("layers").arr
      .find(_("name").str == name)
      .getOrElse(fail(s"the vocabulary has no layer '$name'"))

  When("a reader reads the vocabulary")(() => ask("/graph/vocabulary"): Unit)
  When("a reader reads the belief layer of the vocabulary")(() => ask("/graph/vocabulary"): Unit)
  When("a reader reads the market layer of the vocabulary")(() => ask("/graph/vocabulary"): Unit)

  Then(
    "each kind of node and each kind of edge is named with its layer, its properties and the kind of record that publishes it"
  ) { () =>
    assertEquals(answered("layers").arr.map(_("name").str).toList, List("belief", "market"))
    val nodes = answered("layers").arr.flatMap(_("nodes").arr)
    val edges = answered("layers").arr.flatMap(_("edges").arr)
    assertEquals(
      nodes.map(_("label").str).toList,
      List(
        "Question",
        "Hypothesis",
        "Holder",
        "Source",
        "Evidence",
        "Claim",
        "BeliefRevision",
        "Market",
        "Resolution"
      )
    )
    assertEquals(edges.size, 18)
    (nodes ++ edges).foreach { kind =>
      assert(kind("publishedBy").str.nonEmpty, kind.toString)
      assert(kind.obj.contains("properties"), kind.toString)
    }
  }

  Then("no kind of node and no kind of edge is published by two kinds of record") { () =>
    val labels = answered("layers").arr.flatMap(_("nodes").arr.map(_("label").str))
    val types  = answered("layers").arr.flatMap(_("edges").arr.map(_("type").str))
    assertEquals(labels.distinct.size, labels.size)
    assertEquals(types.distinct.size, types.size)
  }

  Given("the launch example with a market") { () =>
    example = reasoning.support.LaunchExample(http, w.p, Some(clock)).record()
    clock.set(clockStart)
    w.question = example.question
    w.hypothesis = example.yes
    w.hypotheses(Yes) = example.yes
    w.hypotheses(No) = example.no
    w.holder = example.holder
    openMarket(): Unit
    assertEquals(observe(0.48).status, 201, w.last.body)
    val evidence = ok(
      http.post(
        "/evidence",
        Obj(
          "source"     -> example.filings,
          "locator"    -> s"https://filings.example/${w.p}/launch",
          "excerpt"    -> "Product Y is available.",
          "observedAt" -> World.date("14 November")
        )
      )
    ).json("id").str
    w.published = w.published :+ ("evidence" -> evidence)
    ok(resolve(Some("YES"), Seq(evidence))): Unit
  }

  /** Every node this scenario made and every node and edge one step from them. */
  private def everything: (Vector[(Set[String], Set[String])], Vector[(String, Set[String])]) =
    val nodes = graphRows(
      "MATCH (n:Element) WHERE n.id CONTAINS $p OPTIONAL MATCH (n)--(m:Element) WITH collect(n) + collect(m) AS all UNWIND all AS x " +
        "WITH DISTINCT x WHERE x IS NOT NULL RETURN labels(x) AS labels, keys(x) AS properties",
      "p" -> s"${w.p}-"
    )(row => (row.strings("labels").toSet - "Element", row.strings("properties").toSet))
    val edges = graphRows(
      "MATCH (n:Element)-[r]-(:Element) WHERE n.id CONTAINS $p WITH DISTINCT r RETURN type(r) AS type, keys(r) AS properties",
      "p" -> s"${w.p}-"
    )(row => (row.string("type"), row.strings("properties").toSet))
    (nodes, edges)

  private val SinkOwn = Set("id", "_version", "_deleted")
  private val OnEvery = Set("dated", "recordedAt")

  Then("every node and every edge in the graph is of a kind the vocabulary names") { () =>
    val vocabulary     = ask("/graph/vocabulary").json
    val labels         = vocabulary("layers").arr.flatMap(_("nodes").arr.map(_("label").str)).toSet
    val types          = vocabulary("layers").arr.flatMap(_("edges").arr.map(_("type").str)).toSet
    val (nodes, edges) = everything
    assert(nodes.size >= 14, s"only ${nodes.size} nodes were found")
    nodes.foreach((own, _) =>
      assert(own.size == 1 && own.subsetOf(labels), s"a node has the labels $own")
    )
    edges.foreach((edgeType, _) =>
      assert(types.contains(edgeType), s"an edge has the type $edgeType")
    )
    assert(
      nodes.exists(_._1 == Set("Market")) && nodes.exists(_._1 == Set("Resolution")),
      "the market's nodes are not in the graph"
    )
  }

  Then("every property is one the vocabulary names for that kind") { () =>
    val vocabulary = ask("/graph/vocabulary").json
    val ofNode = vocabulary("layers").arr
      .flatMap(
        _("nodes").arr.map(n => n("label").str -> n("properties").arr.map(_("name").str).toSet)
      )
      .toMap
    val ofEdge = vocabulary("layers").arr
      .flatMap(
        _("edges").arr.map(e => e("type").str -> e("properties").arr.map(_("name").str).toSet)
      )
      .toMap
    val (nodes, edges) = everything
    nodes.foreach((own, properties) =>
      assertEquals(
        (properties -- SinkOwn -- OnEvery) -- ofNode(own.head),
        Set.empty[String],
        own.head
      )
    )
    edges.foreach((edgeType, properties) =>
      assertEquals((properties -- SinkOwn) -- ofEdge(edgeType), Set.empty[String], edgeType)
    )
  }

  Then("no kind of node, kind of edge or property in it belongs to the market layer") { () =>
    val belief = layer("belief")
    val market = layer("market")
    val marketNames =
      (market("nodes").arr.map(_("label").str) ++ market("edges").arr.map(_("type").str) ++
        market("nodes").arr.flatMap(_("properties").arr.map(_("name").str)) ++ market(
          "holderKinds"
        ).arr.map(_.str)).toSet
    val beliefNames =
      (belief("nodes").arr.map(_("label").str) ++ belief("edges").arr.map(_("type").str) ++
        belief("nodes").arr.flatMap(_("properties").arr.map(_("name").str)) ++ belief(
          "holderKinds"
        ).arr.map(_.str)).toSet
    assertEquals(beliefNames & marketNames, Set.empty[String])
    val words = Seq("market", "outcome", "resolution", "venue", "price")
    beliefNames.foreach(name =>
      assert(!words.exists(name.toLowerCase.contains), s"the belief layer names '$name'")
    )
    belief("edges").arr.foreach(e =>
      assert(
        Set(e("from").str, e("to").str).subsetOf(belief("nodes").arr.map(_("label").str).toSet),
        e.toString
      )
    )
  }

  Then("every kind of edge in it runs from a node of the market layer") { () =>
    val market = layer("market")
    val own    = market("nodes").arr.map(_("label").str).toSet
    assertEquals(market("edges").arr.size, 7)
    market("edges").arr.foreach(e =>
      assert(own.contains(e("from").str), s"${e("type").str} runs from ${e("from").str}")
    )
  }

  Then("no kind of node of the belief layer has a property from the market layer") { () =>
    val marketProperties =
      layer("market")("nodes").arr.flatMap(_("properties").arr.map(_("name").str)).toSet
    layer("belief")("nodes").arr.foreach { node =>
      assertEquals(
        node("properties").arr.map(_("name").str).toSet & marketProperties,
        Set.empty[String],
        node("label").str
      )
    }
  }
