package reasoning.graph

/** A part of the vocabulary that can be used without the parts above it. */
final case class Layer(name: String)

/** What a property holds. A time is `Whole`: milliseconds since the epoch. */
enum PropertyType(val name: String):
  case Text   extends PropertyType("text")
  case Whole  extends PropertyType("whole number")
  case Number extends PropertyType("number")
  case Flag   extends PropertyType("flag")

final case class Property(name: String, kind: PropertyType, optional: Boolean = false)

/**
 * A kind of node the graph may hold.
 *
 * @param label
 *   its one label, beside the sink's `Element`
 * @param idPrefix
 *   what its `id` begins with: a node's id is `<idPrefix>:<identifier>`
 * @param properties
 *   besides `dated` and `recordedAt`, which every node has
 * @param publishedBy
 *   the one kind of record that publishes it
 */
final case class NodeKind(
    label: String,
    idPrefix: String,
    layer: Layer,
    properties: Vector[Property],
    publishedBy: String
):
  /** The `id` of the node for the record `identifier` names. */
  def id(identifier: String): String = s"$idPrefix:$identifier"

/**
 * A kind of edge the graph may hold: it runs `from` a node of one label `to` a node of another, and
 * is published by the record its `from` node belongs to.
 */
final case class EdgeKind(
    edgeType: String,
    layer: Layer,
    from: String,
    to: String,
    properties: Vector[Property],
    publishedBy: String
):
  /** The `id` of the edge of this kind between two nodes. */
  def id(from: String, to: String): String = s"$edgeType|$from|$to"

/** A kind of holder a layer names. */
final case class HolderKind(name: String, layer: Layer)

/**
 * Every kind of node and edge the graph may hold, with each kind's layer, its properties and the
 * kind of record that publishes it. A vocabulary is a value: a layer contributes one and the
 * service composes them with `++`.
 */
final case class Vocabulary(
    nodes: Vector[NodeKind] = Vector.empty,
    edges: Vector[EdgeKind] = Vector.empty,
    holderKinds: Vector[HolderKind] = Vector.empty
):

  def ++(other: Vocabulary): Vocabulary =
    Vocabulary(nodes ++ other.nodes, edges ++ other.edges, holderKinds ++ other.holderKinds)

  def node(label: String): Option[NodeKind]    = nodes.find(_.label == label)
  def edge(edgeType: String): Option[EdgeKind] = edges.find(_.edgeType == edgeType)

  def holderKind(name: String): Option[HolderKind] = holderKinds.find(_.name == name)

  def layers: Vector[Layer] =
    (nodes.map(_.layer) ++ edges.map(_.layer) ++ holderKinds.map(_.layer)).distinct

  /** The part of the vocabulary one layer contributes. */
  def of(layer: Layer): Vocabulary =
    Vocabulary(
      nodes.filter(_.layer == layer),
      edges.filter(_.layer == layer),
      holderKinds.filter(_.layer == layer)
    )

  /**
   * What is wrong with the vocabulary, empty when nothing is: a label, an edge type, an id prefix
   * or a holder kind declared twice (which is how two kinds of record would come to publish one
   * kind of element), and an edge that runs from or to a label no node kind has.
   */
  def problems: Vector[String] =
    def twice(what: String, names: Vector[String]): Vector[String] =
      names
        .groupBy(identity)
        .collect {
          case (name, all) if all.sizeIs > 1 => s"$what '$name' is declared ${all.size} times"
        }
        .toVector
        .sorted
    val labels = nodes.map(_.label).toSet
    val dangling = edges.flatMap { e =>
      Vector(e.from, e.to)
        .filterNot(labels)
        .map(l => s"edge '${e.edgeType}' names the label '$l', which no node kind has")
    }
    twice("label", nodes.map(_.label)) ++
      twice("id prefix", nodes.map(_.idPrefix)) ++
      twice("edge type", edges.map(_.edgeType)) ++
      twice("holder kind", holderKinds.map(_.name)) ++
      dangling
