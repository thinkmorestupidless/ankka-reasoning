package reasoning

import reasoning.process.appraiser.GoldenSet
import reasoning.seed.{Measure, MeasureGoldenSet}
import reasoning.steps.ScriptedAppraiser
import reasoning.support.{GraphFixture, ServiceFixture}

import scala.concurrent.duration.DurationInt

/**
 * `just measure-golden-set` against the graph fixture's service with the scripted model: the four
 * runs of the appraiser, each record to the graph with its act from its run's end, and the whole
 * set to the two holders compared. What a laptop with a model key measures over HTTP, measured here
 * without one; the figures are printed for the record.
 */
class GoldenSetMeasureSuite extends munit.FunSuite:

  override val munitTimeout = 20.minutes

  test("the golden set's four runs, each record to the graph with its act, and the whole set") {
    GraphFixture.clock.set(ServiceFixture.Start)
    ServiceFixture.model.reset()
    ServiceFixture.judge.reset()
    val script =
      ScriptedAppraiser(
        ServiceFixture.model,
        ServiceFixture.judge,
        GoldenSet(ServiceFixture.settings.goldenSet)
      )
    script.install()
    val run    = s"gm${java.lang.Long.toString(System.currentTimeMillis() / 1000, 36)}"
    val result = MeasureGoldenSet.measure(GraphFixture.baseUrl, run, println)
    val times  = result.recordTimes
    println(
      s"measure-golden-set (scripted model): the four runs ${result.runMillis.mkString(", ")} ms; " +
        s"${result.records.size} records to the graph with their acts, from each run's end: " +
        s"median ${Measure.within(times, 0.5)} ms, nine in ten within ${Measure.within(times, 0.9)} ms, " +
        s"slowest ${times.last} ms; the whole set ${result.wholeMillis} ms"
    )
    // Sixteen claims and four revisions, every run's own; and the eight pieces of evidence when this
    // service had not held the documents before, none when another suite's run read them first.
    val kinds = result.records.groupMapReduce(_.record.takeWhile(_ != ':'))(_ => 1)(_ + _)
    assertEquals(kinds.getOrElse("claim", 0), 16, result.records.toString)
    assertEquals(kinds.getOrElse("revision", 0), 4, result.records.toString)
    assert(Set(0, 8)(kinds.getOrElse("evidence", 0)), result.records.toString)
    assert(
      Measure.within(times, 0.9) <= 5000L,
      s"nine in ten within ${Measure.within(times, 0.9)} ms"
    )
    assert(result.wholeMillis <= 120000L, s"the whole set took ${result.wholeMillis} ms")
  }
