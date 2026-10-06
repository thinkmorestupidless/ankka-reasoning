package reasoning

import reasoning.support.GraphFixture
import ujson.{Arr, Obj}

import scala.concurrent.duration.DurationInt

/** The refusals no scenario breaks: each named in the contract, each broken here once. */
class ReadRefusalsSuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  private val http = GraphFixture.http

  test("a writer cannot be added to a holder that is not held") {
    val refused = http.post("/holders/rr-nobody/writers", Obj("writer" -> "service:test/other"))
    assertEquals((refused.status, refused.rule), (422, "holder.not-held"), refused.body)
    assertEquals(refused.names.get("holder"), Some("rr-nobody"))
  }

  test("a time that is not an instant is refused, naming the parameter") {
    Seq("asOf", "asRecordedBy").foreach { name =>
      val refused = http.get(s"/answers/belief?holder=rr-h&hypothesis=rr-q/yes&$name=yesterday")
      assertEquals((refused.status, refused.rule), (422, "time.format"), refused.body)
      assertEquals(refused.names.get(name), Some("yesterday"))
    }
    val none = http.get("/answers/learned?question=rr-q")
    assertEquals((none.status, none.rule), (422, "time.format"), none.body)
    val market = http.get("/markets/rr-market/resolution?asOf=soon")
    assertEquals((market.status, market.rule), (422, "time.format"), market.body)
  }

  test("a market with no resolution has none to explain") {
    val question = Obj(
      "id"        -> "rr-open",
      "statement" -> "Is a market with no resolution asked why it resolved?",
      "hypotheses" -> Arr(
        Obj("id" -> "yes", "statement" -> "it is"),
        Obj("id" -> "no", "statement"  -> "it is not")
      )
    )
    assertEquals(http.post("/questions", question).status, 201)
    val market = Obj(
      "id"                 -> "rr-open-market",
      "question"           -> "rr-open",
      "venue"              -> "a venue",
      "outcomes"           -> Arr(Obj("outcome" -> "YES", "hypothesis" -> "rr-open/yes")),
      "resolutionCriteria" -> "as the venue says",
      "closesAt"           -> "2027-01-01T00:00:00Z"
    )
    assertEquals(http.post("/markets", market).status, 201)
    assert(
      http
        .post("/graph/wait", Obj("kind" -> "market", "id" -> "rr-open-market", "limitMs" -> 30000))
        .json("caughtUp")
        .bool
    )
    val refused = http.get("/markets/rr-open-market/resolution")
    assertEquals((refused.status, refused.rule), (404, "market.not-resolved"), refused.body)
    assertEquals(refused.names.get("market"), Some("rr-open-market"))
  }
