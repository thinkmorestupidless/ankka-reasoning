package reasoning

import reasoning.support.{GraphFixture, LaunchExample}
import ujson.Obj

import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

/**
 * The vocabulary is enough to read the graph by: the trace query printed in its contract, run as it
 * is written there, reaches the claim, the evidence and the source behind a belief revision.
 */
class VocabularyQuerySuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  /** The first Cypher block under "Reading it" in the contract, exactly as printed. */
  private def printedQuery: String =
    val text    = Files.readString(Path.of("specs/001-belief-layer/contracts/graph-vocabulary.md"))
    val reading = text.substring(text.indexOf("## Reading it"))
    val start   = reading.indexOf("```cypher") + "```cypher".length
    reading.substring(start, reading.indexOf("```", start)).trim

  test("the contract's trace query, as printed, returns the launch example's sources") {
    val http    = GraphFixture.http
    val example = LaunchExample(http, "vq").record()
    assert(
      http
        .post("/graph/wait", Obj("kind" -> "revision", "id" -> example.second, "limitMs" -> 30000))
        .json("caughtUp")
        .bool
    )
    assert(
      http
        .post(
          "/graph/wait",
          Obj("kind" -> "claim", "id" -> example.barrierGone, "limitMs" -> 30000)
        )
        .json("caughtUp")
        .bool
    )
    assert(
      http
        .post("/graph/wait", Obj("kind" -> "evidence", "id" -> example.notice, "limitMs" -> 30000))
        .json("caughtUp")
        .bool
    )

    val query = printedQuery
    assert(query.startsWith("MATCH (r:BeliefRevision {id: $revision})"), query)
    val rows =
      GraphFixture.graph.read(query, Map("revision" -> s"revision:${example.second}")) { row =>
        (
          row.properties("c")("statement"),
          row.properties("e")("id"),
          row.properties("s")("name"),
          row.properties("r")("probability")
        )
      }
    assertEquals(
      rows,
      Vector(
        ("the approval barrier has gone", s"evidence:${example.notice}", "the regulator", 0.61)
      )
    )
  }
