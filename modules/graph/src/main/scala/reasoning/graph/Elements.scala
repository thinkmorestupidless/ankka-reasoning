package reasoning.graph

import com.thinkmorestupidless.ankka.core.graph.PropertyValue
import com.thinkmorestupidless.ankka.sdk.graph.{GraphElement, GraphElements}

/**
 * An element of the graph as a record describes it: a node or an edge of a declared kind, whole.
 *
 * It is a plain value, so the function from a record to its elements can be used in two places: by
 * the record's graph consumer, which publishes them, and by whoever waits for the graph, who only
 * needs their keys.
 */
sealed trait Element:
  def id: String

  /** The record key it is published under: `node:<id>` or `edge:<id>`. */
  def key: String

final case class NodeElement(kind: NodeKind, id: String, properties: Map[String, PropertyValue])
    extends Element:
  def key: String = s"node:$id"

final case class EdgeElement(
    kind: EdgeKind,
    id: String,
    from: String,
    to: String,
    properties: Map[String, PropertyValue]
) extends Element:
  def key: String = s"edge:$id"

/** An element was described that its kind does not allow. A mistake in a consumer, never data. */
final class UndeclaredElement(message: String) extends IllegalArgumentException(message)

/**
 * Builds elements from kinds, so that nothing the vocabulary does not name can be built: a property
 * the kind does not declare, one of the wrong type, or a required one left out is refused here,
 * where the element is described.
 */
object Elements:

  /** On every node: the record's date and the time the service accepted it, in milliseconds. */
  val Dated      = "dated"
  val RecordedAt = "recordedAt"

  /** A node of `kind` for the record `identifier` names, with every property it has. */
  def node(
      kind: NodeKind,
      identifier: String,
      dated: Long,
      recordedAt: Long,
      properties: Map[String, PropertyValue] = Map.empty
  ): NodeElement =
    check(s"node '${kind.label}'", kind.properties, properties)
    NodeElement(
      kind,
      kind.id(identifier),
      properties + (Dated -> dated) + (RecordedAt -> recordedAt)
    )

  /** An edge of `kind` between two nodes, named by their ids. */
  def edge(
      kind: EdgeKind,
      from: String,
      to: String,
      properties: Map[String, PropertyValue] = Map.empty
  ): EdgeElement =
    check(s"edge '${kind.edgeType}'", kind.properties, properties)
    EdgeElement(kind, kind.id(from, to), from, to, properties)

  /** What a graph consumer publishes for an element. */
  def publish(graph: GraphElements, element: Element): GraphElement = element match
    case NodeElement(kind, id, properties) => graph.node(id, Seq(kind.label), properties)
    case EdgeElement(kind, id, from, to, properties) =>
      graph.edge(id, kind.edgeType, from, to, properties)

  private def check(
      what: String,
      declared: Vector[Property],
      stated: Map[String, PropertyValue]
  ): Unit =
    stated.foreach { (name, value) =>
      declared.find(_.name == name) match
        case None => throw UndeclaredElement(s"$what has no property '$name' in the vocabulary")
        case Some(property) =>
          if !fits(property.kind, value) then
            throw UndeclaredElement(s"$what: property '$name' is not a ${property.kind.name}")
    }
    declared.filterNot(_.optional).filterNot(p => stated.contains(p.name)).foreach { missing =>
      throw UndeclaredElement(s"$what is missing its property '${missing.name}'")
    }

  private def fits(kind: PropertyType, value: PropertyValue): Boolean = (kind, value) match
    case (PropertyType.Text, _: String)                               => true
    case (PropertyType.Whole, _: Long) | (PropertyType.Whole, _: Int) => true
    case (PropertyType.Number, _: Double) | (PropertyType.Number, _: Long) |
        (PropertyType.Number, _: Int) =>
      true
    case (PropertyType.Flag, _: Boolean) => true
    case _                               => false
