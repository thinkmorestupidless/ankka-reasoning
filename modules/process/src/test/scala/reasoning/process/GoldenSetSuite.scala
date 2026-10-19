package reasoning.process

import reasoning.process.appraiser.{GoldenSet, Instructions}

import java.nio.file.Files

/** The golden set the service carries: the question, eight documents, and readings that add up. */
class GoldenSetSuite extends munit.FunSuite:

  private val set = GoldenSet()

  test("the launch question, its hypotheses, its sources and its market") {
    assertEquals(set.question.id, "launch")
    assertEquals(set.question.hypotheses.map(_.id), Vector("yes", "no"))
    assertEquals(
      set.question.sources.map(_.id),
      Vector(
        "company-x-filings",
        "regulator",
        "trade-press",
        "analyst-notes",
        "supplier-calls",
        "rumour-mill"
      )
    )
    assertEquals(set.question.market.outcomes.map(_.hypothesis), Vector("launch/yes", "launch/no"))
  }

  test("eight documents in two tranches, each of the kind the features name") {
    val may  = set.tranche("may").getOrElse(fail("no May tranche"))
    val june = set.tranche("june").getOrElse(fail("no June tranche"))
    assertEquals(
      may.documents.map(d => d.id -> d.kind),
      Vector(
        "filing"        -> "a filing",
        "notice"        -> "a regulator's notice",
        "trade-press"   -> "a trade-press piece",
        "analyst"       -> "an analyst note",
        "supplier-call" -> "a supplier's earnings call"
      )
    )
    assertEquals(
      june.documents.map(d => d.id -> d.kind),
      Vector(
        "approval"    -> "approval granted",
        "launch-date" -> "a launch date announced",
        "rumour"      -> "a contradicting rumour"
      )
    )
    val sources = set.question.sources.map(_.id).toSet
    (may.documents ++ june.documents).foreach { d =>
      assert(sources.contains(d.source), s"${d.id} comes from an unregistered source ${d.source}")
      assert(d.text.split("\\s+").length >= 150, s"${d.id} is shorter than a few hundred words")
      assertEquals(d.expectedReading.hypothesis, "launch/yes")
    }
  }

  test(
    "the weighted readings fall on the right side of one half, and are the probabilities stated"
  ) {
    val may  = set.tranche("may").get
    val june = set.tranche("june").get
    Instructions.all.map(_.name).foreach { v =>
      assert(may.weightedReading(v) < 0.5, s"$v after May: ${may.weightedReading(v)}")
      assert(june.weightedReading(v) > 0.5, s"$v after June: ${june.weightedReading(v)}")
      assertEqualsDouble(may.weightedReading(v), may.probability.of(v), 1e-9, s"$v after May")
      assertEqualsDouble(june.weightedReading(v), june.probability.of(v), 1e-9, s"$v after June")
    }
    assertEquals((may.probability.v1, may.probability.v2), (0.35, 0.40))
    assertEquals((june.probability.v1, june.probability.v2), (0.72, 0.66))
  }

  test("a document by its tranche and id; an unknown tranche or document is none") {
    assertEquals(set.document("may", "filing").map(_.locator), Some("10-Q 2026 Q1, p. 14"))
    assertEquals(set.document("june", "approval").map(_.source), Some("regulator"))
    assertEquals(set.document("may", "approval"), None)
    assertEquals(set.tranche("july"), None)
    assertEquals(set.tranche("../question"), None)
  }

  test("a file in the directory the service is given wins over the one it carries") {
    val dir = Files.createTempDirectory("golden-set")
    Files.writeString(
      dir.resolve("may.json"),
      """{"name":"may","month":"2026-05","probability":{"v1":0.5,"v2":0.5},"documents":[]}"""
    )
    val handed = GoldenSet(Some(dir))
    assertEquals(handed.tranche("may").map(_.documents.size), Some(0))
    assertEquals(handed.tranche("june").map(_.documents.size), Some(3))
    assertEquals(handed.question.id, "launch")
  }
