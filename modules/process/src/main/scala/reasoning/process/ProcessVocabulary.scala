package reasoning.process

import reasoning.graph.*
import reasoning.graph.PropertyType.*

/**
 * The process layer's part of the vocabulary: blueprint versions, runs, their endings and acts.
 * Every edge it adds leaves one of its own nodes, and it changes nothing in the layers beneath it.
 * It names no kind of holder: a run writes as a holder of the belief layer's.
 */
object ProcessVocabulary:

  val layer: Layer = Layer("process")

  val BlueprintVersion: NodeKind = NodeKind(
    "BlueprintVersion",
    "blueprint-version",
    layer,
    Vector(Property("blueprint", Text), Property("version", Whole), Property("digest", Text)),
    "blueprint version"
  )
  val Run: NodeKind = NodeKind(
    "Run",
    "run",
    layer,
    Vector(Property("blueprint", Text), Property("version", Whole), Property("startedBy", Text)),
    "run"
  )
  val RunEnding: NodeKind = NodeKind(
    "RunEnding",
    "run-ending",
    layer,
    Vector(Property("status", Text), Property("reason", Text, optional = true)),
    "run ending"
  )
  val Act: NodeKind = NodeKind(
    "Act",
    "act",
    layer,
    Vector(Property("step", Text), Property("worker", Text), Property("recordKind", Text)),
    "act"
  )

  val Of: EdgeKind    = EdgeKind("OF", layer, "Run", "BlueprintVersion", Vector.empty, "run")
  val Ended: EdgeKind = EdgeKind("ENDED", layer, "RunEnding", "Run", Vector.empty, "run ending")
  val In: EdgeKind    = EdgeKind("IN", layer, "Act", "Run", Vector.empty, "act")
  val Wrote: EdgeKind =
    EdgeKind(
      "WROTE",
      layer,
      "Act",
      "Evidence",
      Vector.empty,
      "act",
      alsoTo = Vector("Claim", "BeliefRevision")
    )
  val As: EdgeKind = EdgeKind("AS", layer, "Act", "Holder", Vector.empty, "act")

  val vocabulary: Vocabulary = Vocabulary(
    nodes = Vector(BlueprintVersion, Run, RunEnding, Act),
    edges = Vector(Of, Ended, In, Wrote, As)
  )
