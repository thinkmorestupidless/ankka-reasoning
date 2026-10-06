package reasoning

import reasoning.belief.application.SettableClock
import reasoning.steps.FeatureSuite
import reasoning.support.{BeliefOnlyFixture, Http, LaunchExample}
import ujson.Obj

import scala.concurrent.duration.DurationInt

// Every scenario of the belief layer's records, against a service with no market layer in it.

trait BeliefOnly extends FeatureSuite:
  override protected def http: Http           = BeliefOnlyFixture.http
  override protected def clock: SettableClock = BeliefOnlyFixture.clock

class QuestionsBeliefOnly extends FeatureSuite("features/record/questions.feature") with BeliefOnly
class HoldersAndSourcesBeliefOnly
    extends FeatureSuite("features/record/holders-and-sources.feature")
    with BeliefOnly
class EvidenceBeliefOnly extends FeatureSuite("features/record/evidence.feature") with BeliefOnly
class ClaimsBeliefOnly   extends FeatureSuite("features/record/claims.feature") with BeliefOnly
class BeliefsBeliefOnly  extends FeatureSuite("features/record/beliefs.feature") with BeliefOnly

/** The belief layer alone names nothing of the market layer, and loses nothing without it. */
class BeliefOnlySuite extends munit.FunSuite:

  override val munitTimeout = 5.minutes

  private val http = BeliefOnlyFixture.http

  test("the launch example is recorded and read back with no market layer") {
    val example = LaunchExample(http, "belief-only").record()
    val head = http.get(s"/beliefs/current?holder=${example.holder}&hypothesis=${example.yes}").json
    assertEquals(head("current")("probability").num, 0.61)
  }

  test("there is no market route, and no market kind of holder") {
    assertEquals(http.get("/markets/any").status, 404)
    val holder = http.post(
      "/holders",
      Obj("id" -> "belief-only-market", "kind" -> "market", "name" -> "a venue")
    )
    assertEquals(holder.status, 422, holder.body)
    assertEquals(holder.rule, "holder.kind.not-in-vocabulary")
  }

  test("the vocabulary is the belief layer's and names nothing of markets") {
    val vocabulary = http.get("/graph/vocabulary").json
    assertEquals(vocabulary("layers").arr.map(_("name").str).toList, List("belief"))
    val text = ujson.write(vocabulary).toLowerCase
    Seq("market", "outcome", "resolution", "venue", "price").foreach(word =>
      assert(!text.contains(word), s"the belief layer's vocabulary says '$word'")
    )
  }
