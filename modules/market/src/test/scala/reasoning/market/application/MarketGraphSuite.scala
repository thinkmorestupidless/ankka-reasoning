package reasoning.market.application

import com.thinkmorestupidless.ankka.testkit.ConsumerTestKit
import reasoning.belief.BeliefVocabulary
import reasoning.belief.application.Slot
import reasoning.belief.domain.Moment
import reasoning.graph.{EdgeElement, NodeElement}
import reasoning.market.MarketVocabulary
import reasoning.market.domain.*

/** What the market layer adds to the vocabulary, and what a market is in the graph. */
class MarketGraphSuite extends munit.FunSuite:

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

  private val resolved = market.copy(resolutions =
    Vector(
      Resolution("r1", Some("YES"), Vector("e1", "e2"), "the venue", None, t2, t2, "w"),
      Resolution("r2", None, Vector("e3"), "the venue", Some("r1"), t3, t3, "w")
    )
  )

  test(
    "the market layer adds two kinds of node, seven of edge and one of holder, and changes nothing beneath it"
  ) {
    val belief   = BeliefVocabulary.vocabulary
    val composed = belief ++ MarketVocabulary.vocabulary
    assertEquals(composed.problems, Vector.empty)
    assertEquals(composed.layers.map(_.name), Vector("belief", "market"))
    assertEquals(composed.of(BeliefVocabulary.layer), belief)
    val own = composed.of(MarketVocabulary.layer)
    assertEquals(
      own.nodes.map(n => (n.label, n.idPrefix, n.publishedBy)),
      Vector(("Market", "market", "market"), ("Resolution", "resolution", "market"))
    )
    assertEquals(
      own.edges.map(e => (e.edgeType, e.from, e.to)),
      Vector(
        ("ABOUT", "Market", "Question"),
        ("OFFERS", "Market", "Hypothesis"),
        ("SPEAKS_AS", "Market", "Holder"),
        ("RESOLUTION_OF", "Resolution", "Market"),
        ("RESOLVES_TO", "Resolution", "Hypothesis"),
        ("ON_EVIDENCE", "Resolution", "Evidence"),
        ("REVISES_RESOLUTION", "Resolution", "Resolution")
      )
    )
    assertEquals(own.holderKinds.map(_.name), Vector("market"))
    // Every edge the layer adds leaves one of its own nodes: nothing in the belief layer gains a link.
    val ownLabels = own.nodes.map(_.label).toSet
    own.edges.foreach(e => assert(ownLabels(e.from), e.edgeType))
    // And no name is taken twice across the layers.
    assertEquals(belief.nodes.map(_.label).toSet & ownLabels, Set.empty[String])
    assertEquals(
      belief.edges.map(_.edgeType).toSet & own.edges.map(_.edgeType).toSet,
      Set.empty[String]
    )
  }

  test(
    "a market is its node, the question it is about, the holder it speaks as and an edge for each outcome"
  ) {
    val elements = MarketGraphOf.market(market)
    val node     = elements.collectFirst { case n: NodeElement => n }.get
    assertEquals(node.id, "market:m")
    assertEquals(
      node.properties,
      Map[String, Any](
        "venue"              -> "a venue",
        "resolutionCriteria" -> "criteria",
        "closesAt"           -> 3000L,
        "dated"              -> 1000L,
        "recordedAt"         -> 1000L
      )
    )
    assertEquals(
      elements.collect { case e: EdgeElement => (e.kind.edgeType, e.from, e.to, e.properties) },
      Vector(
        ("ABOUT", "market:m", "question:q", Map.empty[String, Any]),
        ("SPEAKS_AS", "market:m", "holder:market.m", Map.empty[String, Any]),
        ("OFFERS", "market:m", "hypothesis:q/yes", Map[String, Any]("outcome" -> "YES")),
        ("OFFERS", "market:m", "hypothesis:q/no", Map[String, Any]("outcome" -> "NO"))
      )
    )
  }

  test(
    "a resolution is its node, its market, its evidence, the hypothesis it came to and the one it revises"
  ) {
    val elements = MarketGraphOf.market(resolved)
    val nodes    = elements.collect { case n: NodeElement if n.kind.label == "Resolution" => n }
    assertEquals(nodes.map(_.id), Vector("resolution:m/r1", "resolution:m/r2"))
    assertEquals(
      nodes.head.properties,
      Map[String, Any](
        "authority"  -> "the venue",
        "void"       -> false,
        "outcome"    -> "YES",
        "dated"      -> 2000L,
        "recordedAt" -> 2000L
      )
    )
    // A void resolution names no outcome and comes to no hypothesis.
    assertEquals(
      nodes.last.properties,
      Map[String, Any](
        "authority"  -> "the venue",
        "void"       -> true,
        "dated"      -> 3000L,
        "recordedAt" -> 3000L
      )
    )
    val edges = elements.collect {
      case e: EdgeElement if e.from.startsWith("resolution:") => (e.kind.edgeType, e.from, e.to)
    }
    assertEquals(
      edges,
      Vector(
        ("RESOLUTION_OF", "resolution:m/r1", "market:m"),
        ("RESOLVES_TO", "resolution:m/r1", "hypothesis:q/yes"),
        ("ON_EVIDENCE", "resolution:m/r1", "evidence:e1"),
        ("ON_EVIDENCE", "resolution:m/r1", "evidence:e2"),
        ("RESOLUTION_OF", "resolution:m/r2", "market:m"),
        ("ON_EVIDENCE", "resolution:m/r2", "evidence:e3"),
        ("REVISES_RESOLUTION", "resolution:m/r2", "resolution:m/r1")
      )
    )
  }

  test(
    "the market's consumer publishes the market whole at its revision, and nothing for a market not held"
  ) {
    val consumer = MarketGraph.On("reasoning-graph").Markets
    val deltas = ConsumerTestKit
      .graph(consumer)
      .onMessage(Slot(Some(resolved)), subject = "m", sequenceNumber = 3)
    assertEquals(deltas.map(_.key), MarketGraphOf.market(resolved).map(_.key))
    assertEquals(deltas.map(_.version).distinct, Vector(3L))
    assertEquals(deltas.head.labels, Vector("Market"))
    assertEquals(
      ConsumerTestKit.graph(consumer).onMessage(Slot(None), subject = "m", sequenceNumber = 1),
      Vector.empty
    )
  }
