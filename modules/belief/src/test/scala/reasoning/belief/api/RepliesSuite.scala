package reasoning.belief.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.core.{CommandError, ErrorCode}
import reasoning.belief.domain.{Refused, Source, Moment}

/** What a route answers with: a record under 201 or 200, or a refusal's body under its status. */
class RepliesSuite extends munit.FunSuite:

  private given JsonValueCodec[Source] = Replies.codec[Source]

  private val source = Source("s", "a source", Moment(0L), Moment(0L), "local")

  private def text(reply: Reply): String = String(reply.body.body, "UTF-8")

  test("a new record is 201 and the same record again is 200, with the same body") {
    val created = Replies.recorded(source, created = true)
    val repeat  = Replies.recorded(source, created = false)
    assertEquals(created.status, 201)
    assertEquals(repeat.status, 200)
    assertEquals(text(created), text(repeat))
    assertEquals(created.body.contentType, "application/json")
    assert(text(created).contains("\"recordedAt\":\"1970-01-01T00:00:00Z\""), text(created))
  }

  test("a refusal is answered with its status, its sentence, its rule and what it names") {
    val reply = Replies.handle(
      throw Refused(
        "claim.derives-from.none",
        422,
        "A claim derives from evidence.",
        "claim" -> "c1"
      )
    )
    assertEquals(reply.status, 422)
    assertEquals(
      text(reply),
      """{"rule":"claim.derives-from.none","status":422,"error":"A claim derives from evidence.","names":{"claim":"c1"}}"""
    )
  }

  test("an entity's own refusal keeps its status") {
    val reply = Replies.handle(throw CommandError("no", ErrorCode.Conflict))
    assertEquals(reply.status, 409)
    assert(text(reply).contains("\"rule\":\"command.refused\""), text(reply))
  }

  test("a body that is answered is passed through") {
    assertEquals(Replies.handle(Replies.ok(source)).status, 200)
  }
