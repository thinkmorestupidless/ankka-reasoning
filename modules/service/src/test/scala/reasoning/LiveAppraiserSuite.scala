package reasoning

import reasoning.belief.application.SettableClock
import reasoning.support.{Http, LiveAppraisal, LiveFixture, ServiceFixture}

import scala.concurrent.duration.DurationInt

/**
 * The same appraiser over the same golden set, against Anthropic's model and Jev's judgment: the
 * one check that the blueprint's instructions and tools make sense to a real model. It needs both
 * keys and a network, so it is assumed on the keys: without them munit reports it skipped, never
 * failed, and the build never sets them.
 *
 * @param env
 *   where the keys are looked for; the test JVM's environment unless a suite runs this one with
 *   another, to see it skipped
 */
class LiveAppraiserSuite(env: Map[String, String]) extends munit.FunSuite with LiveAppraisal:

  def this() = this(sys.env)

  override val munitTimeout = 40.minutes

  protected def http: Http           = LiveFixture.http
  protected def clock: SettableClock = LiveFixture.clock

  test("the appraiser for appraiser-a over May and then June, against the real providers") {
    assume(LiveFixture.keysSet(env), LiveFixture.missing(env))
    val prefix      = LiveAppraisal.prefix()
    val (may, june) = LiveAppraisal.runIds(prefix, LiveHolder)
    try
      val question = seedQuestion(prefix)
      restoreVersionOne()
      LiveFixture.within {
        runTranche(question, LiveHolder, "may", may)
        completed(may)
        runTranche(question, LiveHolder, "june", june, follows = Some(s"$may.yes"))
        completed(june)
      }
      val first  = revision(may)("probability").num
      val second = revision(june)("probability").num
      assert(first < 0.5, s"the first belief revision is at $first, not below 0.5")
      assert(second > 0.5, s"the second belief revision is at $second, not above 0.5")
      everyRecordHeldWithItsAct(may, "may")
      everyRecordHeldWithItsAct(june, "june")
      claimsDeriveFromTranche(may, "may")
      claimsDeriveFromTranche(june, "june")
    finally clock.set(ServiceFixture.Start)
  }
