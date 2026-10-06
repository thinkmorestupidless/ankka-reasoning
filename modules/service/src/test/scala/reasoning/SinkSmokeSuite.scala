package reasoning

import reasoning.support.{Eventually, GraphFixture}

import scala.concurrent.duration.DurationInt

/**
 * The released sink image runs as the sink alone from mounted configuration, against Kafka and
 * Neo4j in containers, and applies a delta nobody but this test wrote.
 */
class SinkSmokeSuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  test("a hand-written delta on the topic becomes a node in the graph database") {
    val _ = GraphFixture.kit
    GraphFixture.produce(
      Seq(
        "node:smoke:1".getBytes("UTF-8") ->
          """{"kind":"node","id":"smoke:1","version":3,"labels":["Smoke"],"properties":{"name":"written by hand","count":2}}"""
            .getBytes("UTF-8")
      )
    )
    try
      Eventually.eventually(90.seconds) {
        val rows = GraphFixture.graph.read(
          "MATCH (n:Smoke {id: $id}) RETURN n._version AS version, n.name AS name, labels(n) AS labels",
          Map("id" -> "smoke:1")
        )(row => (row.long("version"), row.string("name"), row.strings("labels").toSet))
        assertEquals(rows, Vector((3L, "written by hand", Set("Element", "Smoke"))))
      }
    catch
      case failure: Throwable =>
        println(s"the sink's log:\n${GraphFixture.sinkLogs}")
        throw failure
  }
