package reasoning

import reasoning.support.{AppraiserRun, GraphFixture}

import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

/**
 * The process layer's vocabulary is enough to read the graph by: the two queries printed in its
 * contract, run as they are written there, trace a belief revision to its act, run, ending and
 * version, and list everything a run wrote.
 */
class ProcessVocabularyQuerySuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  /** The Cypher blocks under "Reading it" in the contract, exactly as printed. */
  private def printedQueries: Vector[String] =
    val text    = Files.readString(Path.of("specs/002-process-layer/contracts/graph-vocabulary.md"))
    val reading = text.substring(text.indexOf("## Reading it"))
    Iterator
      .unfold(0) { from =>
        val start = reading.indexOf("```cypher", from)
        if start < 0 then None
        else
          val body = start + "```cypher".length
          val end  = reading.indexOf("```", body)
          Some(reading.substring(body, end).trim -> end)
      }
      .toVector

  private lazy val run = AppraiserRun.completed(GraphFixture.http, "pvq")

  test(
    "the contract's trace query, as printed, reaches the act, the run, its ending and the version"
  ) {
    val query = printedQueries(0)
    assert(query.startsWith("MATCH (r:BeliefRevision {id: $revision})"), query)
    val act = run.acts.find(_("record")("id").str == run.revision).getOrElse(fail("no act"))
    val rows =
      GraphFixture.graph.read(query, Map("revision" -> s"revision:${run.revision}")) { row =>
        (
          row.properties("a")("id"),
          row.properties("run")("id"),
          row.properties("e")("status"),
          row.properties("v")("id")
        )
      }
    assertEquals(
      rows,
      Vector((act("id").str, s"run:${run.id}", "completed", "blueprint-version:appraiser@1"))
    )
  }

  test("the contract's run query, as printed, lists every record the run wrote with its act") {
    val query = printedQueries(1)
    assert(query.startsWith("MATCH (a:Act)-[:IN]->(:Run {id: $run})"), query)
    val rows = GraphFixture.graph.read(query, Map("run" -> s"run:${run.id}")) { row =>
      (
        row.properties("a")("id").toString,
        row.strings("labels(x)").toSet,
        row.properties("x")("id").toString
      )
    }
    assertEquals(rows.size, 11)
    assertEquals(
      rows.map(_._2).groupBy(identity).map((labels, all) => labels -> all.size),
      Map(
        Set("Element", "Evidence")       -> 5,
        Set("Element", "Claim")          -> 5,
        Set("Element", "BeliefRevision") -> 1
      )
    )
    val held =
      run.acts.map(a => a("id").str -> s"${a("record")("kind").str}:${a("record")("id").str}").toMap
    rows.foreach((act, _, record) => assertEquals(held.get(act), Some(record), act))
  }
