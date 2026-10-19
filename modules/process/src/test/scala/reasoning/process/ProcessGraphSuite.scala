package reasoning.process

import com.thinkmorestupidless.ankka.testkit.ConsumerTestKit
import reasoning.belief.application.Slot
import reasoning.graph.{EdgeElement, NodeElement}
import reasoning.process.Fixtures.*
import reasoning.process.application.graph.{ProcessGraph, ProcessGraphOf}

/** What each record of the process layer is in the graph. */
class ProcessGraphSuite extends munit.FunSuite:

  private def edges(elements: Vector[reasoning.graph.Element]) =
    elements.collect { case e: EdgeElement => (e.kind.edgeType, e.from, e.to) }

  test("a blueprint version is one node with its blueprint, number and digest") {
    val Vector(node: NodeElement) = ProcessGraphOf.version(version): @unchecked
    assertEquals(node.id, "blueprint-version:appraiser@1")
    assertEquals(node.properties("version"), 1L)
  }

  test("a run is its node and the edge to its version") {
    val elements = ProcessGraphOf.run(run)
    assertEquals(elements.head.id, "run:r1")
    assertEquals(edges(elements), Vector(("OF", "run:r1", "blueprint-version:appraiser@1")))
  }

  test("a run ending is its node and the edge to its run") {
    val elements = ProcessGraphOf.ending(ending)
    assertEquals(elements.head.id, "run-ending:r1")
    assertEquals(edges(elements), Vector(("ENDED", "run-ending:r1", "run:r1")))
  }

  test("an act is its node and the edges to its run, the record it wrote and its holder") {
    val elements = ProcessGraphOf.act(act)
    assertEquals(elements.head.id, act.id)
    assertEquals(
      edges(elements),
      Vector(
        ("IN", act.id, "run:r1"),
        ("WROTE", act.id, s"evidence:${"e" * 64}"),
        ("AS", act.id, "holder:appraiser-a")
      )
    )
  }

  test("each consumer publishes its record whole at its revision, and nothing for none held") {
    val on     = ProcessGraph.On("reasoning-graph")
    val deltas = ConsumerTestKit.graph(on.Acts).onMessage(Slot(Some(act)), sequenceNumber = 2)
    assertEquals(deltas.map(_.key), ProcessGraphOf.act(act).map(_.key))
    assertEquals(deltas.map(_.version).distinct, Vector(2L))
    assertEquals(deltas.head.labels, Vector("Act"))
    assertEquals(ConsumerTestKit.graph(on.Runs).onMessage(Slot(None)), Vector.empty)
    assertEquals(
      ConsumerTestKit.graph(on.Versions).onMessage(Slot(Some(version))).map(_.key),
      ProcessGraphOf.version(version).map(_.key)
    )
    assertEquals(
      ConsumerTestKit.graph(on.Endings).onMessage(Slot(Some(ending))).map(_.key),
      ProcessGraphOf.ending(ending).map(_.key)
    )
  }
