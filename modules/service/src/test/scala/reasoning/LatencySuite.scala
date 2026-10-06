package reasoning

import reasoning.support.GraphFixture
import ujson.Obj

import scala.concurrent.duration.DurationInt

/**
 * How long a record takes to reach the graph: from the service's answer to its write, to the graph
 * holding its node and every edge it stated. Nine in ten within five seconds (SC-007).
 */
class LatencySuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  test("a record is in the graph within five seconds of being recorded, nine times in ten") {
    val http   = GraphFixture.http
    val source = "latency-source"
    assertEquals(http.post("/sources", Obj("id" -> source, "name" -> "a source")).status, 201)
    assert(
      http
        .post("/graph/wait", Obj("kind" -> "source", "id" -> source, "limitMs" -> 30000))
        .json("caughtUp")
        .bool
    )

    val times = (1 to 30).map { i =>
      val recorded = http.post(
        "/evidence",
        Obj("source" -> source, "locator" -> s"latency/$i", "excerpt" -> s"record $i")
      )
      assertEquals(recorded.status, 201, recorded.body)
      val started = System.nanoTime()
      val waited = http.post(
        "/graph/wait",
        Obj("kind" -> "evidence", "id" -> recorded.json("id").str, "limitMs" -> 30000)
      )
      assert(waited.json("caughtUp").bool, waited.body)
      (System.nanoTime() - started) / 1000000L
    }.sorted
    val p50 = times(14)
    val p90 = times(26)
    println(
      s"time to the graph over 30 records: median $p50 ms, nine in ten within $p90 ms, slowest ${times.last} ms"
    )
    assert(p90 <= 5000L, s"nine in ten took up to $p90 ms")
  }
