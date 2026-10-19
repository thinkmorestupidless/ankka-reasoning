package reasoning.process

import reasoning.belief.BeliefVocabulary
import reasoning.market.MarketVocabulary

/** The process layer adds to the layers beneath it and changes nothing in them. */
class ProcessVocabularySuite extends munit.FunSuite:

  private val own = ProcessVocabulary.vocabulary

  test("every edge of the process layer runs from a node of the process layer") {
    val labels = own.nodes.map(_.label).toSet
    own.edges.foreach(e => assert(labels.contains(e.from), e.edgeType))
  }

  test("no label or edge type of the process layer is the belief layer's or the market layer's") {
    val beneath = BeliefVocabulary.vocabulary ++ MarketVocabulary.vocabulary
    assertEquals(
      own.nodes.map(_.label).toSet & beneath.nodes.map(_.label).toSet,
      Set.empty[String]
    )
    assertEquals(
      own.edges.map(_.edgeType).toSet & beneath.edges.map(_.edgeType).toSet,
      Set.empty[String]
    )
  }

  test("the three layers' vocabularies together have no problems") {
    assertEquals(
      (BeliefVocabulary.vocabulary ++ MarketVocabulary.vocabulary ++ own).problems,
      Vector.empty
    )
  }

  test("an act wrote evidence, a claim or a belief revision, and nothing else") {
    assertEquals(
      ProcessVocabulary.Wrote.targets.toSet,
      Set("Evidence", "Claim", "BeliefRevision")
    )
  }

  test("the process layer names no kind of holder") {
    assertEquals(own.holderKinds, Vector.empty)
  }
