package reasoning

import reasoning.support.{Eventually, GraphFixture}
import ujson.Obj

import scala.concurrent.duration.DurationInt

/**
 * Withdrawn text leaves the delta topic once the broker has compacted the record's key: the earlier
 * delta, which carried the text, is replaced by the later one, which does not.
 *
 * The topic here is compacted on a clock of seconds where a deployment's is a day; the mechanism is
 * the same.
 */
class CompactionErasureSuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  private val Marker = "MARKER-TOPIC-51be07"

  private def onTopic: Int =
    GraphFixture
      .topicRecords()
      .count(record => record.value != null && String(record.value, "UTF-8").contains(Marker))

  test("the delta that carried withdrawn text is compacted out of the topic") {
    val http = GraphFixture.http
    assertEquals(
      http.post("/sources", Obj("id" -> "compaction-source", "name" -> "a source")).status,
      201
    )
    val id = http
      .post(
        "/evidence",
        Obj(
          "source"  -> "compaction-source",
          "locator" -> "somewhere",
          "excerpt" -> s"$Marker a passage that has to come out"
        )
      )
      .json("id")
      .str
    assert(
      http
        .post("/graph/wait", Obj("kind" -> "evidence", "id" -> id, "limitMs" -> 30000))
        .json("caughtUp")
        .bool
    )
    assertEquals(
      onTopic,
      1,
      "the topic never held the text, so finding none later would prove nothing"
    )

    assertEquals(http.post(s"/evidence/$id/withdrawal", Obj("note" -> "taken down")).status, 200)
    assert(
      http
        .post("/graph/wait", Obj("kind" -> "evidence", "id" -> id, "limitMs" -> 30000))
        .json("caughtUp")
        .bool
    )

    var rolls = 0
    Eventually.eventually(180.seconds) {
      // A segment is compacted once it is closed, and it closes when something is written after
      // its time is up: a record on every partition keeps the log moving.
      rolls += 1
      GraphFixture.produce((0 until 12).map { i =>
        s"node:roll:$rolls-$i".getBytes(
          "UTF-8"
        ) -> s"""{"kind":"node","id":"roll:$rolls-$i","version":1,"labels":["Roll"],"properties":{}}"""
          .getBytes("UTF-8")
      })
      Thread.sleep(3000)
      assertEquals(onTopic, 0, "the delta with the text is still on the topic")
    }
    // The element is still there: its latest delta, without the text.
    val latest = GraphFixture
      .topicRecords()
      .filter(record => String(record.key, "UTF-8") == s"node:evidence:$id")
    assertEquals(latest.size, 1)
    assert(
      String(latest.head.value, "UTF-8").contains("\"withdrawn\":true"),
      String(latest.head.value, "UTF-8")
    )
  }
