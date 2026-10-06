package reasoning.market

import reasoning.graph.*
import reasoning.graph.PropertyType.*

/**
 * The market layer's part of the vocabulary. It adds its own kinds of node, edges that leave its
 * own nodes, and one kind of holder, and changes nothing in the belief layer.
 */
object MarketVocabulary:

  val layer: Layer = Layer("market")

  val Market: NodeKind = NodeKind(
    "Market",
    "market",
    layer,
    Vector(
      Property("venue", Text),
      Property("resolutionCriteria", Text),
      Property("closesAt", Whole)
    ),
    "market"
  )
  val Resolution: NodeKind = NodeKind(
    "Resolution",
    "resolution",
    layer,
    Vector(
      Property("authority", Text),
      Property("void", Flag),
      Property("outcome", Text, optional = true)
    ),
    "market"
  )

  val About: EdgeKind = EdgeKind("ABOUT", layer, "Market", "Question", Vector.empty, "market")
  val Offers: EdgeKind =
    EdgeKind("OFFERS", layer, "Market", "Hypothesis", Vector(Property("outcome", Text)), "market")
  val SpeaksAs: EdgeKind = EdgeKind("SPEAKS_AS", layer, "Market", "Holder", Vector.empty, "market")
  val ResolutionOf: EdgeKind =
    EdgeKind("RESOLUTION_OF", layer, "Resolution", "Market", Vector.empty, "market")
  val ResolvesTo: EdgeKind =
    EdgeKind(
      "RESOLVES_TO",
      layer,
      "Resolution",
      "Hypothesis",
      Vector(Property("outcome", Text)),
      "market"
    )
  val OnEvidence: EdgeKind =
    EdgeKind("ON_EVIDENCE", layer, "Resolution", "Evidence", Vector.empty, "market")
  val RevisesResolution: EdgeKind =
    EdgeKind("REVISES_RESOLUTION", layer, "Resolution", "Resolution", Vector.empty, "market")

  val HolderKindName = "market"

  val vocabulary: Vocabulary = Vocabulary(
    nodes = Vector(Market, Resolution),
    edges =
      Vector(About, Offers, SpeaksAs, ResolutionOf, ResolvesTo, OnEvidence, RevisesResolution),
    holderKinds = Vector(HolderKind(HolderKindName, layer))
  )
