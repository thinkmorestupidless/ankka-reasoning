package reasoning

import reasoning.belief.domain.Moment
import reasoning.support.{GraphFixture, LaunchExample, ServiceFixture, Walk}
import ujson.{Arr, Obj}

import java.time.Instant
import scala.concurrent.duration.DurationInt

/**
 * Asking as of a time, held as a property and not by example: every answer about the launch example
 * at the end of each day of May holds nothing dated later, and an answer as recorded by a time is
 * the same after a back-dated record is entered (SC-006).
 */
class AsOfPropertySuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  private val http  = GraphFixture.http
  private val clock = GraphFixture.clock

  private lazy val example: LaunchExample =
    val recorded = LaunchExample(http, "aop", Some(clock)).record()
    Seq(
      "revision" -> recorded.first,
      "revision" -> recorded.second,
      "claim"    -> recorded.pending,
      "claim"    -> recorded.barrierGone
    ).foreach(caughtUp)
    recorded

  private def caughtUp(record: (String, String)): Unit =
    val waited =
      http.post("/graph/wait", Obj("kind" -> record._1, "id" -> record._2, "limitMs" -> 30000))
    assert(waited.json("caughtUp").bool, s"${record._1} ${record._2}: ${waited.body}")

  /** Every answer there is about the example. */
  private def answers: Vector[String] = Vector(
    s"/answers/belief?holder=${example.holder}&hypothesis=${example.yes}",
    s"/answers/belief-change?from=${example.first}&to=${example.second}",
    s"/answers/case?hypothesis=${example.yes}&stance=supports",
    s"/answers/case?hypothesis=${example.yes}&stance=contradicts",
    s"/answers/learned?question=${example.question}&after=2026-05-01T00:00:00Z",
    s"/answers/resting-on-revised?question=${example.question}"
  )

  override def afterAll(): Unit = clock.set(ServiceFixture.Start)

  test("no answer as of the end of any day of May holds a record dated later") {
    val _    = example
    var seen = 0
    (1 to 31).foreach { day =>
      val asOf = Instant.parse(f"2026-05-$day%02dT23:59:59Z")
      answers.foreach { path =>
        val answer = http.get(s"$path&asOf=$asOf")
        // A change between two revisions has no answer before both are stated; every other does.
        if !path.contains("belief-change") || day >= 11 then
          assertEquals(answer.status, 200, s"$path as of $asOf: ${answer.body}")
        else
          assertEquals(
            (answer.status, answer.rule),
            (422, "answer.revision.later-than-asked"),
            answer.body
          )
        if answer.status == 200 then
          val dates = Walk.dated(answer.json)
          seen += dates.size
          dates.foreach(dated =>
            assert(!dated.isAfter(asOf), s"$path as of $asOf holds a record dated $dated")
          )
      }
      // And what was believed is what was believed then: nothing, then 0.38, then 0.61.
      val belief = http
        .get(s"/answers/belief?holder=${example.holder}&hypothesis=${example.yes}&asOf=$asOf")
        .json
      val revision = belief.obj.get("revision").filterNot(_.isNull)
      val expected = if day < 4 then None else if day < 11 then Some(0.38) else Some(0.61)
      assertEquals(revision.map(_("probability").num), expected, s"as of $asOf")
    }
    assert(seen > 100, s"only $seen dated records were seen, so the property was barely tried")
  }

  test("an answer as recorded by a time is unchanged after a back-dated record is entered") {
    val _  = example
    val by = "2026-05-12T00:00:00Z"
    def asRecorded: Map[String, String] = answers.map { path =>
      val answer = http.get(s"$path&asRecordedBy=$by")
      assertEquals(answer.status, 200, s"$path: ${answer.body}")
      path -> answer.body
    }.toMap
    val before = asRecorded

    // Entered on 20 May, about 6 May: evidence, a claim from it and nothing else.
    clock.set(Moment.parse("2026-05-20T12:00:00Z").get)
    val evidence = http.post(
      "/evidence",
      Obj(
        "source"     -> example.filings,
        "locator"    -> "https://filings.example/aop/late",
        "excerpt"    -> "A supplier's note, found late.",
        "observedAt" -> "2026-05-06T09:00:00Z"
      )
    )
    assertEquals(evidence.status, 201, evidence.body)
    val late = "aop-found-late"
    val claim = http.post(
      "/claims",
      Obj(
        "id"          -> late,
        "holder"      -> example.holder,
        "statement"   -> "a supplier was already shipping parts",
        "derivesFrom" -> Arr(evidence.json("id").str),
        "stances"     -> Arr(Obj("hypothesis" -> example.yes, "stance" -> "supports")),
        "dated"       -> "2026-05-06T09:00:00Z"
      )
    )
    assertEquals(claim.status, 201, claim.body)
    caughtUp("claim" -> late)

    assertEquals(asRecorded, before)
    before.values.foreach { body =>
      Walk
        .recordedAt(ujson.read(body))
        .foreach(at =>
          assert(
            !at.isAfter(Instant.parse(by)),
            s"an answer as recorded by $by holds a record entered $at"
          )
        )
    }
    // Asked by date alone, the same question does see it: the bound is what kept it out.
    val byDate = http
      .get(s"/answers/case?hypothesis=${example.yes}&stance=supports&asOf=2026-05-07T00:00:00Z")
      .json
    assert(Walk.values(byDate, "id").exists(_.strOpt.contains(late)), ujson.write(byDate))
    val bounded = ujson.read(before(s"/answers/case?hypothesis=${example.yes}&stance=supports"))
    assert(!Walk.values(bounded, "id").exists(_.strOpt.contains(late)))
  }
