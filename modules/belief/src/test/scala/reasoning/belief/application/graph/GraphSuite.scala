package reasoning.belief.application.graph

import com.thinkmorestupidless.ankka.testkit.ConsumerTestKit
import reasoning.belief.BeliefVocabulary
import reasoning.belief.application.{BeliefEvent, Slot}
import reasoning.belief.domain.*
import reasoning.graph.{EdgeElement, NodeElement}

/** What each record is in the graph, and what its consumer puts on the topic. */
class GraphSuite extends munit.FunSuite:

  private val t1 = Moment(1_000L)
  private val t2 = Moment(2_000L)

  private val claim = Claim(
    "c2",
    "h",
    Some("the barrier has gone"),
    Vector("e2"),
    Vector(Stance("q/yes", Stance.Supports), Stance("q/no", Stance.Contradicts)),
    Some("c1"),
    t2,
    t2,
    "w"
  )
  private val revision = Revision(
    "r2",
    "h",
    "q/yes",
    2,
    0.61,
    Vector(Rest("c2", Some(0.8))),
    Some("r1"),
    t2,
    t2,
    "w",
    sequence = 2
  )

  test(
    "the vocabulary is the contract's: seven kinds of node, eleven of edge, three of holder, one publisher each"
  ) {
    val v = BeliefVocabulary.vocabulary
    assertEquals(v.problems, Vector.empty)
    assertEquals(
      v.nodes.map(n => (n.label, n.idPrefix, n.publishedBy)),
      Vector(
        ("Question", "question", "question"),
        ("Hypothesis", "hypothesis", "question"),
        ("Holder", "holder", "holder"),
        ("Source", "source", "source"),
        ("Evidence", "evidence", "evidence"),
        ("Claim", "claim", "claim"),
        ("BeliefRevision", "revision", "belief")
      )
    )
    assertEquals(
      v.edges.map(e => (e.edgeType, e.from, e.to, e.publishedBy)),
      Vector(
        ("ANSWERS", "Hypothesis", "Question", "question"),
        ("FROM_SOURCE", "Evidence", "Source", "evidence"),
        ("STATED_BY", "Claim", "Holder", "claim"),
        ("DERIVES_FROM", "Claim", "Evidence", "claim"),
        ("SUPPORTS", "Claim", "Hypothesis", "claim"),
        ("CONTRADICTS", "Claim", "Hypothesis", "claim"),
        ("REVISES", "Claim", "Claim", "claim"),
        ("HELD_BY", "BeliefRevision", "Holder", "belief"),
        ("BELIEF_IN", "BeliefRevision", "Hypothesis", "belief"),
        ("RESTS_ON", "BeliefRevision", "Claim", "belief"),
        ("FOLLOWS", "BeliefRevision", "BeliefRevision", "belief")
      )
    )
    assertEquals(v.holderKinds.map(_.name), Vector("agent", "person", "model"))
    assertEquals(v.layers.map(_.name), Vector("belief"))
    // An edge leaves a node its own kind of record publishes: the link has one owner.
    v.edges.foreach(e =>
      assertEquals(v.node(e.from).map(_.publishedBy), Some(e.publishedBy), e.edgeType)
    )
  }

  test("a question is its node, each hypothesis and the edge from each") {
    val question = Question(
      "q",
      "a question",
      Vector(Hypothesis("yes", "yes", t1, t1, "w"), Hypothesis("no", "no", t1, t1, "w")),
      t1,
      t1,
      "w"
    )
    assertEquals(
      GraphOf.question(question).map(_.key),
      Vector(
        "node:question:q",
        "node:hypothesis:q/yes",
        "edge:ANSWERS|hypothesis:q/yes|question:q",
        "node:hypothesis:q/no",
        "edge:ANSWERS|hypothesis:q/no|question:q"
      )
    )
  }

  test("a claim is its node and an edge for each link it stated, each leaving its own node") {
    val elements = GraphOf.claim(claim)
    val node     = elements.collectFirst { case n: NodeElement => n }.get
    assertEquals(node.id, "claim:c2")
    assertEquals(
      node.properties,
      Map(
        "statement"  -> "the barrier has gone",
        "withdrawn"  -> false,
        "dated"      -> 2000L,
        "recordedAt" -> 2000L
      )
    )
    val edges = elements.collect { case e: EdgeElement => (e.kind.edgeType, e.from, e.to) }
    assertEquals(
      edges,
      Vector(
        ("STATED_BY", "claim:c2", "holder:h"),
        ("DERIVES_FROM", "claim:c2", "evidence:e2"),
        ("SUPPORTS", "claim:c2", "hypothesis:q/yes"),
        ("CONTRADICTS", "claim:c2", "hypothesis:q/no"),
        ("REVISES", "claim:c2", "claim:c1")
      )
    )
  }

  test(
    "a belief revision carries its probability on its node and its weight on the edge to the claim"
  ) {
    val elements = GraphOf.revision(revision)
    val node     = elements.collectFirst { case n: NodeElement => n }.get
    assertEquals(node.properties("probability"), 0.61)
    assertEquals(node.properties("n"), 2)
    val restsOn = elements.collectFirst {
      case e: EdgeElement if e.kind.edgeType == "RESTS_ON" => e
    }.get
    assertEquals((restsOn.to, restsOn.properties), ("claim:c2", Map[String, Any]("weight" -> 0.8)))
    val edges = elements.collect { case e: EdgeElement => e.kind.edgeType }
    assertEquals(edges, Vector("HELD_BY", "BELIEF_IN", "RESTS_ON", "FOLLOWS"))
  }

  test("evidence whose text is withdrawn has no locator, excerpt or author in the graph") {
    val evidence = Evidence("e", "s", Some("l"), Some("x"), Some("a"), None, t1, t1, "w")
    val withdrawn = evidence.copy(
      locator = None,
      excerpt = None,
      author = None,
      withdrawal = Some(Withdrawal("a note", "w", t2))
    )
    val before = GraphOf.evidence(evidence).collectFirst { case n: NodeElement => n.properties }.get
    val after = GraphOf.evidence(withdrawn).collectFirst { case n: NodeElement => n.properties }.get
    assertEquals(
      before.keySet,
      Set("locator", "excerpt", "author", "withdrawn", "dated", "recordedAt")
    )
    assertEquals(
      after,
      Map[String, Any](
        "withdrawn"   -> true,
        "withdrawnAt" -> 2000L,
        "dated"       -> 1000L,
        "recordedAt"  -> 1000L
      )
    )
  }

  test("a claim's consumer publishes its elements under their keys at the record's revision") {
    val consumers = Consumers.On("reasoning-graph")
    val deltas = ConsumerTestKit
      .graph(consumers.Claims)
      .onMessage(Slot(Some(claim)), subject = "c2", sequenceNumber = 3)
    assertEquals(deltas.map(_.key), GraphOf.claim(claim).map(_.key))
    assertEquals(deltas.map(_.version).distinct, Vector(3L))
    assertEquals(deltas.head.labels, Vector("Claim"))
    assertEquals(
      ConsumerTestKit
        .graph(consumers.Claims)
        .onMessage(Slot(None), subject = "c2", sequenceNumber = 1),
      Vector.empty
    )
  }

  test("a belief's consumer publishes each revision at its event's sequence number") {
    val consumers = Consumers.On("reasoning-graph")
    val deltas = ConsumerTestKit
      .graph(consumers.Beliefs)
      .onMessage(BeliefEvent.RevisionStated(revision), subject = "h~q~yes", sequenceNumber = 2)
    assertEquals(deltas.map(_.key), GraphOf.revision(revision).map(_.key))
    assertEquals(deltas.map(_.version).distinct, Vector(2L))
    assertEquals(deltas.map(_.version).distinct, Vector(revision.sequence))
  }
