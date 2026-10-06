package reasoning

import reasoning.belief.domain.{Evidence, HypothesisRef, Ids}
import reasoning.support.ServiceFixture
import ujson.{Arr, Obj}

import scala.concurrent.duration.DurationInt

/**
 * The longest and oddest identifiers the service accepts round-trip through real entities: an
 * entity's id and its component's name share a 255-character column in ankka's schema.
 */
class EntityIdSuite extends munit.FunSuite:

  override val munitTimeout = 5.minutes

  private val http = ServiceFixture.http

  test("identifiers of 64 characters with every allowed character are held and read back") {
    val odd = "a.b_c-D9" + "x" * 56
    assertEquals(odd.length, Ids.Limit)
    val question = "q" + odd.drop(1)
    val holder   = "h" + odd.drop(1)
    val source   = "s" + odd.drop(1)
    assertEquals(
      http
        .post(
          "/questions",
          Obj(
            "id"        -> question,
            "statement" -> "a question",
            "hypotheses" -> Arr(
              Obj("id" -> odd, "statement"     -> "one"),
              Obj("id" -> "other", "statement" -> "two")
            )
          )
        )
        .status,
      201
    )
    assertEquals(
      http.post("/holders", Obj("id" -> holder, "kind" -> "agent", "name" -> "a holder")).status,
      201
    )
    assertEquals(http.post("/sources", Obj("id" -> source, "name" -> "a source")).status, 201)

    // An evidence id is 64 hex characters; a belief's entity id is three identifiers joined.
    val evidence = http
      .post(
        "/evidence",
        Obj("source" -> source, "locator" -> "somewhere", "excerpt" -> "something")
      )
      .json("id")
      .str
    assertEquals(evidence.length, 64)
    assertEquals(evidence, Evidence.digest(source, "somewhere", "something"))

    val hypothesis = s"$question/$odd"
    assertEquals(Ids.belief(holder, HypothesisRef(question, odd)).length, 194)
    val claim = "c" + odd.drop(1)
    assertEquals(
      http
        .post(
          "/claims",
          Obj(
            "id"          -> claim,
            "holder"      -> holder,
            "statement"   -> "a claim",
            "derivesFrom" -> Arr(evidence),
            "stances"     -> Arr(Obj("hypothesis" -> hypothesis, "stance" -> "supports"))
          )
        )
        .status,
      201
    )
    val revision = "r" + odd.drop(1)
    val stated = http.post(
      "/beliefs/revisions",
      Obj(
        "id"          -> revision,
        "holder"      -> holder,
        "hypothesis"  -> hypothesis,
        "probability" -> 0.5,
        "restsOn"     -> Arr(Obj("claim" -> claim))
      )
    )
    assertEquals(stated.status, 201, stated.body)
    assertEquals(
      http.get(s"/beliefs/current?holder=$holder&hypothesis=$hypothesis").json("current")("id").str,
      revision
    )
    assertEquals(http.get(s"/beliefs/revisions/$revision").json("probability").num, 0.5)
  }

  test("an identifier of 65 characters is refused, naming the limit") {
    val answer = http.post("/sources", Obj("id" -> "s" * 65, "name" -> "a source"))
    assertEquals(answer.status, 422)
    assertEquals(answer.rule, "id.format")
    assertEquals(answer.names.get("limit"), Some("64"))
  }

  test("an identifier with a character outside the format is refused") {
    Seq("has space", "tilde~here", "pipe|here", ".starts-with-dot", "").foreach { id =>
      val answer = http.post("/sources", Obj("id" -> id, "name" -> "a source"))
      assertEquals(answer.rule, "id.format", s"'$id': ${answer.body}")
    }
  }
