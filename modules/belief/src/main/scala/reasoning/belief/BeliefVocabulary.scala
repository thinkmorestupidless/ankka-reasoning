package reasoning.belief

import reasoning.graph.*
import reasoning.graph.PropertyType.*

/**
 * The belief layer's part of the vocabulary: every kind of node and edge it publishes, and the kind
 * of record that publishes each. A graph consumer of this layer can build nothing else.
 */
object BeliefVocabulary:

  val layer: Layer = Layer("belief")

  val Question: NodeKind =
    NodeKind("Question", "question", layer, Vector(Property("statement", Text)), "question")
  val Hypothesis: NodeKind =
    NodeKind("Hypothesis", "hypothesis", layer, Vector(Property("statement", Text)), "question")
  val Holder: NodeKind =
    NodeKind(
      "Holder",
      "holder",
      layer,
      Vector(Property("kind", Text), Property("name", Text)),
      "holder"
    )
  val Source: NodeKind =
    NodeKind("Source", "source", layer, Vector(Property("name", Text)), "source")
  val Evidence: NodeKind = NodeKind(
    "Evidence",
    "evidence",
    layer,
    Vector(
      Property("locator", Text, optional = true),
      Property("excerpt", Text, optional = true),
      Property("author", Text, optional = true),
      Property("publishedAt", Whole, optional = true),
      Property("withdrawn", Flag),
      Property("withdrawnAt", Whole, optional = true)
    ),
    "evidence"
  )
  val Claim: NodeKind = NodeKind(
    "Claim",
    "claim",
    layer,
    Vector(
      Property("statement", Text, optional = true),
      Property("withdrawn", Flag),
      Property("withdrawnAt", Whole, optional = true)
    ),
    "claim"
  )
  val BeliefRevision: NodeKind = NodeKind(
    "BeliefRevision",
    "revision",
    layer,
    Vector(Property("n", Whole), Property("probability", Number)),
    "belief"
  )

  val Answers: EdgeKind =
    EdgeKind("ANSWERS", layer, "Hypothesis", "Question", Vector.empty, "question")
  val FromSource: EdgeKind =
    EdgeKind("FROM_SOURCE", layer, "Evidence", "Source", Vector.empty, "evidence")
  val StatedBy: EdgeKind = EdgeKind("STATED_BY", layer, "Claim", "Holder", Vector.empty, "claim")
  val DerivesFrom: EdgeKind =
    EdgeKind("DERIVES_FROM", layer, "Claim", "Evidence", Vector.empty, "claim")
  val Supports: EdgeKind = EdgeKind("SUPPORTS", layer, "Claim", "Hypothesis", Vector.empty, "claim")
  val Contradicts: EdgeKind =
    EdgeKind("CONTRADICTS", layer, "Claim", "Hypothesis", Vector.empty, "claim")
  val Revises: EdgeKind = EdgeKind("REVISES", layer, "Claim", "Claim", Vector.empty, "claim")
  val HeldBy: EdgeKind =
    EdgeKind("HELD_BY", layer, "BeliefRevision", "Holder", Vector.empty, "belief")
  val BeliefIn: EdgeKind =
    EdgeKind("BELIEF_IN", layer, "BeliefRevision", "Hypothesis", Vector.empty, "belief")
  val RestsOn: EdgeKind =
    EdgeKind(
      "RESTS_ON",
      layer,
      "BeliefRevision",
      "Claim",
      Vector(Property("weight", Number, optional = true)),
      "belief"
    )
  val Follows: EdgeKind =
    EdgeKind("FOLLOWS", layer, "BeliefRevision", "BeliefRevision", Vector.empty, "belief")

  /** The kinds of holder this layer names. A layer above may add its own. */
  val holderKinds: Vector[HolderKind] =
    Vector(HolderKind("agent", layer), HolderKind("person", layer), HolderKind("model", layer))

  val vocabulary: Vocabulary = Vocabulary(
    nodes = Vector(Question, Hypothesis, Holder, Source, Evidence, Claim, BeliefRevision),
    edges = Vector(
      Answers,
      FromSource,
      StatedBy,
      DerivesFrom,
      Supports,
      Contradicts,
      Revises,
      HeldBy,
      BeliefIn,
      RestsOn,
      Follows
    ),
    holderKinds = holderKinds
  )
