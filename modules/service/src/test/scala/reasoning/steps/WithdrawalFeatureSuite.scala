package reasoning.steps

import reasoning.support.{Answer, Http, ServiceFixture}
import ujson.Obj

/**
 * The living feature of the one exception to nothing being edited: the text of evidence or of a
 * claim taken out for good, with the record, its links and its dates left as they were.
 */
abstract class WithdrawalFeatureSuite(feature: String) extends ExplainFeatureSuite(feature):

  private val Note = "the publisher asked for the passage to be taken down"

  private def evidenceOf(source: String): String = source match
    case "the regulator"     => example.notice
    case "Company X filings" => example.filing
    case other               => fail(s"the launch example has no evidence from '$other'")

  private def withdrawEvidence(
      id: String,
      note: Option[String],
      writer: String = Http.Writer
  ): Answer =
    w.evidence = id
    w.firstAnswer = http.get(s"/evidence/$id")
    w.last = http.post(s"/evidence/$id/withdrawal", note.fold(Obj())(n => Obj("note" -> n)), writer)
    w.last

  private def edgesOf(node: String): Set[String] =
    graphRows(
      "MATCH (n:Element {id: $id})-[r]-(m:Element) RETURN type(r) AS type, startNode(r).id AS from, endNode(r).id AS to",
      "id" -> node
    )(row => s"${row.string("from")} ${row.string("type")} ${row.string("to")}").toSet

  private def evidenceNode: Map[String, Any] =
    graphRows(
      "MATCH (e:Element {id: $id}) RETURN properties(e) AS properties",
      "id" -> s"evidence:${w.evidence}"
    )(_.properties("properties")).head

  // ── In the service ────────────────────────────────────────────────────────

  When(
    "the writer who recorded the evidence from the source {string} withdraws its text, giving a note"
  ) { (source: String) =>
    w.thatWriter = Http.Writer
    withdrawEvidence(evidenceOf(source), Some(Note)): Unit
  }

  Then("the evidence is held with no excerpt, no author and no locator") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    val evidence = held(s"/evidence/${w.evidence}")
    Seq("excerpt", "author", "locator").foreach(field =>
      assertEquals(evidence.obj.get(field), None, field)
    )
    assertEquals(evidence("id").str, w.evidence)
    assertEquals(evidence("source").str, w.firstAnswer.json("source").str)
    assertEquals(evidence("dated").str, w.firstAnswer.json("dated").str)
    assertEquals(evidence("recordedAt").str, w.firstAnswer.json("recordedAt").str)
  }

  Then("the evidence is held as withdrawn, with the note, the writer and the time") { () =>
    val withdrawal = held(s"/evidence/${w.evidence}")("withdrawal")
    assertEquals(withdrawal("note").str, Note)
    assertEquals(withdrawal("writer").str, Http.writer(w.thatWriter))
    assertEquals(withdrawal("at").str, clock.now().iso)
  }

  Then("the claim {string} still derives from the evidence") { (statement: String) =>
    val claim = held(s"/claims/${claimId(statement)}")("claim")
    assertEquals(claim("derivesFrom").arr.map(_.str).toList, List(w.evidence))
    assertEquals(claim("statement").str, statement)
  }

  When(
    "a writer who speaks for {string} withdraws the statement of the claim the current belief revision rests on, giving a note"
  ) { (name: String) =>
    assertEquals(holderId(name), example.holder)
    w.claim = example.barrierGone
    w.firstAnswer = http.get(s"/claims/${w.claim}")
    w.last = http.post(s"/claims/${w.claim}/withdrawal", Obj("note" -> Note))
  }

  Then("the claim is held with no statement, as withdrawn") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    val claim = held(s"/claims/${w.claim}")("claim")
    assertEquals(claim.obj.get("statement"), None)
    assertEquals(claim("withdrawal")("note").str, Note)
  }

  Then("the claim is held with its evidence, its stance and its date") { () =>
    val before = w.firstAnswer.json("claim")
    val claim  = held(s"/claims/${w.claim}")("claim")
    Seq("derivesFrom", "stances", "dated", "recordedAt", "holder", "revises", "writer").foreach {
      field =>
        assertEquals(claim(field), before(field), field)
    }
  }

  Then("the current belief revision still rests on the claim") { () =>
    val current =
      held(s"/beliefs/current?holder=${example.holder}&hypothesis=${example.yes}")("current")
    assertEquals(current("restsOn").arr.map(_("claim").str).toList, List(w.claim))
  }

  // ── In the graph ──────────────────────────────────────────────────────────

  private def withdrawnAndCaughtUp(source: String): Unit =
    val id = evidenceOf(source)
    w.notes("edges") = edgesOf(s"evidence:$id").toVector.sorted.mkString("\n")
    assertEquals(withdrawEvidence(id, Some(Note)).status, 200, w.last.body)
    caughtUp("evidence", id)

  When(
    "the text of the evidence from the source {string} is withdrawn, and the graph has caught up"
  )(withdrawnAndCaughtUp)

  Given("the text of the evidence from the source {string} is withdrawn") { (source: String) =>
    assertEquals(withdrawEvidence(evidenceOf(source), Some(Note)).status, 200, w.last.body)
    caughtUp("evidence", w.evidence)
  }

  Then("the node for the evidence has no excerpt, no author and no locator") { () =>
    val node = evidenceNode
    Seq("excerpt", "author", "locator").foreach(field =>
      assert(!node.contains(field), s"the node still has its $field: $node")
    )
    assertEquals(node("id"), s"evidence:${w.evidence}")
  }

  Then("the node is shown as withdrawn") { () =>
    val node = evidenceNode
    assertEquals(node("withdrawn"), true)
    assertEquals(node("withdrawnAt"), clock.now().millis)
    assertEquals(node("_version"), 2L)
  }

  Then("every edge to the node and from the node is as it was") { () =>
    val now = edgesOf(s"evidence:${w.evidence}")
    assertEquals(now.toVector.sorted.mkString("\n"), w.notes("edges"))
    assertEquals(now.size, 2, now.toString)
  }

  // ── In answers ────────────────────────────────────────────────────────────

  Then("the explanation gives the evidence as withdrawn, with its source and its date") { () =>
    val evidence = answered("newlyRestedOn").arr.head("evidence").arr.head
    assertEquals(evidence("id").str, w.evidence)
    assertEquals(evidence("withdrawn").bool, true)
    assertEquals(evidence("source")("name").str, "the regulator")
    assertEquals(evidence("dated").str, example.May11)
    assertEquals(claimsIn(answered("newlyRestedOn")), List(example.barrierGone))
  }

  Then("the explanation gives no excerpt for it") { () =>
    val evidence = answered("newlyRestedOn").arr.head("evidence").arr.head
    assertEquals(evidence.obj.get("excerpt"), None)
    assert(!w.last.body.contains("Notice 1187"), w.last.body)
  }

  When(
    "a reader asks what was learned about the question after {string}, as recorded by a time before the text was withdrawn"
  ) { (day: String) =>
    // The example was recorded in May and the text withdrawn in December.
    ask(
      s"/answers/learned?question=${example.question}&after=${World.noon(day)}&asRecordedBy=2026-06-01T00:00:00Z"
    ): Unit
  }

  Then("the evidence is in the answer as withdrawn") { () =>
    val evidence = answered("evidence").arr
      .find(_("id").str == w.evidence)
      .getOrElse(fail(s"the evidence is not in the answer: ${w.last.body}"))
    assertEquals(evidence("withdrawn").bool, true)
  }

  Then("the answer gives no excerpt for it") { () =>
    val evidence = answered("evidence").arr.find(_("id").str == w.evidence).get
    assertEquals(evidence.obj.get("excerpt"), None)
    assert(!w.last.body.contains("Notice 1187"), w.last.body)
  }

  // ── Recorded again, and refusals ──────────────────────────────────────────

  When("a writer records the same evidence again") { () =>
    w.last = http.post(
      "/evidence",
      Obj(
        "source"     -> example.regulator,
        "locator"    -> s"https://regulator.example/${w.p}/notices/1187",
        "excerpt"    -> "Notice 1187: Product Y is approved for sale.",
        "observedAt" -> example.May11
      ),
      "another"
    )
  }

  Then("the writer is given the withdrawn evidence") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json("id").str, w.evidence)
    assertEquals(w.last.json("withdrawal")("note").str, Note)
    assertEquals(w.last.json.obj.get("excerpt"), None)
  }

  Then("the evidence is held with no excerpt") { () =>
    assertEquals(held(s"/evidence/${w.evidence}").obj.get("excerpt"), None)
  }

  When(
    "the writer who recorded the evidence from the source {string} withdraws its text, giving no note"
  ) { (source: String) =>
    w.breaks = "withdrawal.note.none"
    withdrawEvidence(evidenceOf(source), None): Unit
  }

  Given("a writer who did not record the evidence from the source {string}") { (source: String) =>
    w.evidence = evidenceOf(source)
    w.thatWriter = "stranger"
  }

  When("that writer withdraws its text, giving a note")(() =>
    withdrawEvidence(w.evidence, Some(Note), w.thatWriter): Unit
  )

  Then("the writer is refused, naming the evidence") { () =>
    refusedBy("withdrawal.writer.may-not")
    assertEquals(w.last.status, 403)
    assertEquals(w.last.names.get("evidence"), Some(w.evidence))
  }

  Given("a steward who did not record the evidence from the source {string}") { (source: String) =>
    w.evidence = evidenceOf(source)
    w.thatWriter = ServiceFixture.Steward
  }

  When("the steward withdraws its text, giving a note")(() =>
    withdrawEvidence(w.evidence, Some(Note), ServiceFixture.Steward): Unit
  )

  Then("the evidence is held as withdrawn, with the note, the steward and the time") { () =>
    val withdrawal = held(s"/evidence/${w.evidence}")("withdrawal")
    assertEquals(withdrawal("note").str, Note)
    assertEquals(withdrawal("writer").str, Http.writer(ServiceFixture.Steward))
    assertEquals(withdrawal("at").str, clock.now().iso)
  }
