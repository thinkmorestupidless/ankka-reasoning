package reasoning

import reasoning.process.ProcessLayer
import reasoning.support.{AppraiserRun, GraphFixture}

import scala.concurrent.duration.DurationInt

/**
 * What the process layer's contract says a test holds of the graph, over one completed run: one
 * edge of each kind per act, one per run and per ending, one act per record the run wrote, nothing
 * outside the vocabulary, and no path back to where it started.
 */
class ProcessGraphPropertiesSuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  private lazy val run = AppraiserRun.completed(GraphFixture.http, "pgp")

  private def rows[A](query: String, parameters: (String, Any)*)(row: reasoning.graph.Row => A) =
    GraphFixture.graph.read(query, parameters.toMap)(row)

  /** The run's own nodes: the version, the run, its ending and its acts. */
  private def ownIds: Vector[String] =
    Vector("blueprint-version:appraiser@1", s"run:${run.id}", s"run-ending:${run.id}") ++
      run.acts.map(_("id").str)

  test("every act has exactly one WROTE, one IN and one AS; the run one OF; the ending one ENDED") {
    val perAct = rows(
      "MATCH (a:Act)-[:IN]->(:Run {id: $run}) " +
        "RETURN a.id AS act, size([(a)-[:WROTE]->() | 1]) AS wrote, size([(a)-[:IN]->() | 1]) AS in, size([(a)-[:AS]->() | 1]) AS as",
      "run" -> s"run:${run.id}"
    )(row => (row.string("act"), row.long("wrote"), row.long("in"), row.long("as")))
    assertEquals(perAct.size, 11)
    perAct.foreach((act, wrote, in, as) => assertEquals((wrote, in, as), (1L, 1L, 1L), act))
    val ofRun = rows(
      "MATCH (r:Run {id: $run}) RETURN size([(r)-[:OF]->() | 1]) AS of, size([(:RunEnding)-[:ENDED]->(r) | 1]) AS ended",
      "run" -> s"run:${run.id}"
    )(row => (row.long("of"), row.long("ended")))
    assertEquals(ofRun, Vector((1L, 1L)))
    val ofEnding = rows(
      "MATCH (e:RunEnding {id: $ending}) RETURN size([(e)-[:ENDED]->() | 1]) AS ended",
      "ending" -> s"run-ending:${run.id}"
    )(_.long("ended"))
    assertEquals(ofEnding, Vector(1L))
  }

  test("every record the run wrote has exactly one act pointing at it") {
    val perRecord = rows(
      "MATCH (:Act)-[:IN]->(:Run {id: $run}) " +
        "MATCH (a:Act)-[:IN]->(:Run {id: $run})-[:OF]->() MATCH (a)-[:WROTE]->(x) " +
        "RETURN x.id AS record, size([(:Act)-[:WROTE]->(x) | 1]) AS acts",
      "run" -> s"run:${run.id}"
    )(row => (row.string("record"), row.long("acts")))
    assertEquals(perRecord.map(_._1).distinct.size, 11)
    perRecord.foreach((record, acts) => assertEquals(acts, 1L, record))
  }

  test(
    "every element of the run is of a kind the process layer's vocabulary names, with its properties"
  ) {
    val vocabulary = ProcessLayer.vocabulary
    val nodes = rows(
      "MATCH (n:Element) WHERE n.id IN $ids RETURN n.id AS id, labels(n) AS labels, keys(n) AS properties",
      "ids" -> ownIds
    )(row =>
      (row.string("id"), row.strings("labels").toSet - "Element", row.strings("properties").toSet)
    )
    assertEquals(nodes.map(_._1).toSet, ownIds.toSet)
    val sinkOwn = Set("id", "_version", "_deleted")
    val onEvery = Set("dated", "recordedAt")
    nodes.foreach { (id, labels, properties) =>
      assertEquals(labels.size, 1, id)
      val kind  = vocabulary.node(labels.head).getOrElse(fail(s"$id has the label ${labels.head}"))
      val named = kind.properties.map(_.name).toSet
      assertEquals(properties -- sinkOwn -- onEvery -- named, Set.empty[String], id)
      kind.properties
        .filterNot(_.optional)
        .foreach(p => assert(properties.contains(p.name), s"$id lacks ${p.name}"))
    }
    val edges = rows(
      "MATCH (a:Element)-[r]->(b:Element) WHERE a.id IN $ids RETURN type(r) AS type, labels(a) AS from, labels(b) AS to, keys(r) AS properties",
      "ids" -> ownIds
    )(row =>
      (
        row.string("type"),
        row.strings("from").toSet - "Element",
        row.strings("to").toSet - "Element",
        row.strings("properties").toSet
      )
    )
    assertEquals(edges.size, 11 * 3 + 2)
    edges.foreach { (edgeType, from, to, properties) =>
      val kind = vocabulary.edge(edgeType).getOrElse(fail(s"an edge of the type $edgeType"))
      assertEquals(from, Set(kind.from), edgeType)
      assert(kind.targets.contains(to.head), s"$edgeType runs to ${to.head}")
      assertEquals(properties -- sinkOwn, Set.empty[String], edgeType)
    }
  }

  test("no path returns to the node it started from") {
    val loops = rows(
      "MATCH p = (a:Element)-[*1..12]->(a) WHERE a.id IN $ids RETURN count(p) AS n",
      "ids" -> ownIds
    )(_.long("n"))
    assertEquals(loops, Vector(0L))
  }
