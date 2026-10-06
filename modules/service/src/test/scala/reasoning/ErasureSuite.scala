package reasoning

import reasoning.support.{GraphFixture, ServiceFixture}
import ujson.{Arr, Obj}

import java.sql.DriverManager
import scala.concurrent.duration.DurationInt

/**
 * After the text of a record is withdrawn, nothing the service keeps can be asked for it: no row of
 * any table of its database holds the text, as text or as bytes.
 *
 * This is what the choice of a key value entity for every record with text rests on. An event would
 * have kept it for good.
 */
class ErasureSuite extends munit.FunSuite:

  override val munitTimeout = 5.minutes

  private val http = ServiceFixture.http

  /** How many rows, of any table in the service's database, hold `marker`. */
  private def rowsHolding(marker: String): Map[String, Int] =
    val connection = DriverManager.getConnection(ServiceFixture.kit.jdbcUrl, "ankka", "ankka")
    try
      val tables = Vector.newBuilder[String]
      val listing = connection
        .createStatement()
        .executeQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
      while listing.next() do tables += listing.getString(1)
      val hex = marker.getBytes("UTF-8").map(b => f"$b%02x").mkString
      tables
        .result()
        .map { table =>
          // A bytea column reads as hex when a row is cast to text, so the marker is looked for both ways.
          val counted = connection.prepareStatement(
            s"""SELECT count(*) FROM "$table" t WHERE t::text LIKE ? OR t::text LIKE ?"""
          )
          counted.setString(1, s"%$marker%")
          counted.setString(2, s"%$hex%")
          val result = counted.executeQuery()
          result.next()
          table -> result.getInt(1)
        }
        .filter(_._2 > 0)
        .toMap
    finally connection.close()

  test("withdrawn evidence leaves no row holding its excerpt, its author or its locator") {
    val excerpt = "MARKER-EXCERPT-7f3a91 a passage that has to come out"
    val author  = "MARKER-AUTHOR-7f3a91"
    val locator = "https://example.test/MARKER-LOCATOR-7f3a91"
    assertEquals(
      http.post("/sources", Obj("id" -> "erasure-source", "name" -> "a source")).status,
      201
    )
    val id = http
      .post(
        "/evidence",
        Obj(
          "source"  -> "erasure-source",
          "locator" -> locator,
          "excerpt" -> excerpt,
          "author"  -> author
        )
      )
      .json("id")
      .str

    // The check can fail: before the withdrawal the database does hold each of them.
    Seq("MARKER-EXCERPT-7f3a91", "MARKER-AUTHOR-7f3a91", "MARKER-LOCATOR-7f3a91").foreach { marker =>
      assert(
        rowsHolding(marker).nonEmpty,
        s"the database never held $marker, so finding none later would prove nothing"
      )
    }

    assertEquals(http.post(s"/evidence/$id/withdrawal", Obj("note" -> "taken down")).status, 200)

    Seq("MARKER-EXCERPT-7f3a91", "MARKER-AUTHOR-7f3a91", "MARKER-LOCATOR-7f3a91").foreach { marker =>
      assertEquals(rowsHolding(marker), Map.empty[String, Int], s"$marker is still in the database")
    }
    // The record is still there, and still says what it is.
    val held = http.get(s"/evidence/$id").json
    assertEquals(held("id").str, id)
    assertEquals(held("withdrawal")("note").str, "taken down")
  }

  test("a withdrawn claim leaves no row holding its statement, the view of claims included") {
    val statement = "MARKER-STATEMENT-2c88e0 a claim that has to come out"
    assertEquals(
      http
        .post(
          "/questions",
          Obj(
            "id"        -> "erasure-q",
            "statement" -> "a question",
            "hypotheses" -> Arr(
              Obj("id" -> "yes", "statement" -> "yes"),
              Obj("id" -> "no", "statement"  -> "no")
            )
          )
        )
        .status,
      201
    )
    assertEquals(
      http
        .post("/holders", Obj("id" -> "erasure-holder", "kind" -> "agent", "name" -> "a holder"))
        .status,
      201
    )
    assertEquals(
      http.post("/sources", Obj("id" -> "erasure-source-2", "name" -> "a source")).status,
      201
    )
    val evidence = http
      .post(
        "/evidence",
        Obj("source" -> "erasure-source-2", "locator" -> "somewhere", "excerpt" -> "something")
      )
      .json("id")
      .str
    val stated = http.post(
      "/claims",
      Obj(
        "id"          -> "erasure-claim",
        "holder"      -> "erasure-holder",
        "statement"   -> statement,
        "derivesFrom" -> Arr(evidence),
        "stances"     -> Arr(Obj("hypothesis" -> "erasure-q/yes", "stance" -> "supports"))
      )
    )
    assertEquals(stated.status, 201, stated.body)
    // Let the view of claims take the claim in, so that it is among what is searched.
    reasoning.support.Eventually.eventually()(
      assertEquals(http.get("/claims/erasure-claim").status, 200)
    )
    Thread.sleep(4000)
    assert(rowsHolding("MARKER-STATEMENT-2c88e0").nonEmpty)

    assertEquals(
      http.post("/claims/erasure-claim/withdrawal", Obj("note" -> "taken down")).status,
      200
    )
    Thread.sleep(4000)
    assertEquals(rowsHolding("MARKER-STATEMENT-2c88e0"), Map.empty[String, Int])
    val _ = GraphFixture.Topic
  }
