package reasoning.steps

import munit.MUnitRunner
import org.junit.runner.notification.{Failure, RunListener, RunNotifier}
import org.junit.runner.Description
import reasoning.LiveAppraiserSuite
import reasoning.belief.application.SettableClock
import reasoning.support.{Http, LiveAppraisal, LiveFixture}

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

/**
 * The live feature: the appraiser against the real providers, in the two scenarios that assume a
 * key, and the live suite seen skipped without one. The steps of a scenario with a key reach the
 * graph fixture's service with its providers switched to the real ones; the scenario without a key
 * runs the plain live suite with no keys at all and reads what munit reports.
 */
abstract class LiveFeatureSuite(feature: String) extends FeatureSuite(feature) with LiveAppraisal:

  override val munitTimeout = 40.minutes

  override protected def http: Http           = LiveFixture.http
  override protected def clock: SettableClock = LiveFixture.clock

  /**
   * With no key the live service is never started: the Background holds for the scenario that runs
   * it.
   */
  private def whenLive(body: => Unit): Unit = if LiveFixture.keysSet() then body

  private def mayRun: String  = w.notes.getOrElse("may", fail("the appraiser was not run"))
  private def juneRun: String = w.notes.getOrElse("june", fail("the June run was not made"))

  // ── Background ────────────────────────────────────────────────────────────

  Given("the golden set")(() => whenLive(w.question = seedQuestion(w.p)))

  Given(
    "the appraiser, registered at blueprint version 1, whose worker {string} speaks for the holder {string}"
  ) { (worker: String, holder: String) =>
    whenLive {
      restoreVersionOne()
      val versions = LiveFixture.agents.blueprints.versions(Blueprint)
      assert(versions.exists(_.number == 1), versions.toString)
      val current = versions.last.number
      assert(
        LiveFixture.agents.blueprints.version(Blueprint, current).workers.exists(_.name == worker),
        s"no worker $worker"
      )
      val record = held(s"/holders/$holder")
      assert(record("writers").arr.exists(_.str.endsWith(Blueprint)), record.render())
    }
  }

  // ── A key ─────────────────────────────────────────────────────────────────

  Given("a model key")(() => assume(LiveFixture.keysSet(), LiveFixture.missing()))

  When(
    "the appraiser is run for {string} over the May tranche and then the June tranche against the real model"
  ) { (holder: String) =>
    val (may, june) = LiveAppraisal.runIds(w.p, holder)
    LiveFixture.within {
      w.notes("may") = runTranche(w.question, holder, "may", may)
      completed(may)
      w.notes("june") = runTranche(w.question, holder, "june", june, follows = Some(s"$may.yes"))
      completed(june)
    }
  }

  When("the appraiser is run for {string} over the May tranche against the real model") {
    (holder: String) =>
      val (may, _) = LiveAppraisal.runIds(w.p, holder)
      LiveFixture.within(w.notes("may") = runTranche(w.question, holder, "may", may))
  }

  Then("the first belief revision has a probability below {string}") { (bound: String) =>
    val p = revision(mayRun)("probability").num
    assert(p < bound.toDouble, s"the first belief revision is at $p")
  }

  Then("the second belief revision has a probability above {string}") { (bound: String) =>
    val p = revision(juneRun)("probability").num
    assert(p > bound.toDouble, s"the second belief revision is at $p")
  }

  Then("every record the runs wrote is held with its act") { () =>
    everyRecordHeldWithItsAct(mayRun, "may")
    everyRecordHeldWithItsAct(juneRun, "june")
  }

  Then("the critique of the run passed")(() => completed(mayRun))

  Then(
    "every claim the run stated derives from evidence recorded from a document of the May tranche"
  )(() => claimsDeriveFromTranche(mayRun, "may"))

  // ── No key ────────────────────────────────────────────────────────────────

  /** What munit reported of the live suite, by test name. */
  private val reported = mutable.LinkedHashMap.empty[String, String]

  Given("no model key") { () =>
    // The suite is run below with no keys whatever this JVM's environment holds.
    reported.clear()
  }

  When("the live suite is run") { () =>
    val notifier = RunNotifier()
    notifier.addListener(new RunListener:
      override def testStarted(d: Description): Unit = reported(d.getMethodName) = "started"
      override def testAssumptionFailure(f: Failure): Unit =
        reported(f.getDescription.getMethodName) = "skipped"
      override def testIgnored(d: Description): Unit = reported(d.getMethodName) = "ignored"
      override def testFailure(f: Failure): Unit =
        reported(f.getDescription.getMethodName) = s"failed: ${f.getMessage}")
    MUnitRunner(classOf[LiveAppraiserSuite], () => LiveAppraiserSuite(Map.empty)).run(notifier)
    assert(reported.nonEmpty, "the live suite ran no test")
  }

  Then("the live suite is skipped") { () =>
    assert(reported.values.forall(_ == "skipped"), reported.toString)
  }

  Then("nothing in it is failed") { () =>
    assert(reported.values.forall(!_.startsWith("failed")), reported.toString)
  }
