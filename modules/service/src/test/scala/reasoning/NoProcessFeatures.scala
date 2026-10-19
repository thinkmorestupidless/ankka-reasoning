package reasoning

import reasoning.belief.application.SettableClock
import reasoning.steps.{
  ExplainFeatureSuite,
  FeatureSuite,
  MarketFeatureSuite,
  WithdrawalFeatureSuite
}
import reasoning.support.{Http, NoProcessFixture}
import ujson.Obj

import scala.concurrent.duration.DurationInt

// Every scenario of the belief layer and the market layer, against a service with no process layer
// in it: the two layers lose nothing without it.

trait NoProcess extends FeatureSuite:
  override protected def http: Http           = NoProcessFixture.http
  override protected def clock: SettableClock = NoProcessFixture.clock

class QuestionsNoProcess extends FeatureSuite("features/record/questions.feature") with NoProcess
class HoldersAndSourcesNoProcess
    extends FeatureSuite("features/record/holders-and-sources.feature")
    with NoProcess
class EvidenceNoProcess extends FeatureSuite("features/record/evidence.feature") with NoProcess
class ClaimsNoProcess   extends FeatureSuite("features/record/claims.feature") with NoProcess
class BeliefsNoProcess  extends FeatureSuite("features/record/beliefs.feature") with NoProcess
class WithdrawalNoProcess
    extends WithdrawalFeatureSuite("features/record/withdrawal.feature")
    with NoProcess

class BeliefChangeNoProcess
    extends ExplainFeatureSuite("features/explain/belief-change.feature")
    with NoProcess
class CaseNoProcess extends ExplainFeatureSuite("features/explain/case.feature") with NoProcess
class AsOfNoProcess extends ExplainFeatureSuite("features/explain/as-of.feature") with NoProcess
class DisagreementNoProcess
    extends ExplainFeatureSuite("features/explain/disagreement.feature")
    with NoProcess

class MarketsNoProcess extends MarketFeatureSuite("features/market/markets.feature") with NoProcess
class ResolutionNoProcess
    extends MarketFeatureSuite("features/market/resolution.feature")
    with NoProcess

/** The two layers beneath name nothing of the process layer, and serve nothing of it. */
class NoProcessSuite extends munit.FunSuite:

  override val munitTimeout = 10.minutes

  private val http = NoProcessFixture.http

  test(
    "the vocabulary is the belief layer's and the market layer's, and names nothing of processes"
  ) {
    val vocabulary = http.get("/graph/vocabulary").json
    assertEquals(vocabulary("layers").arr.map(_("name").str).toList, List("belief", "market"))
    val text = ujson.write(vocabulary).toLowerCase
    Seq("blueprint", "\"run\"", "worker", "step", "digest", "act").foreach(word =>
      assert(!text.contains(word), s"the vocabulary says '$word'")
    )
  }

  test("there is no process route") {
    assertEquals(http.get("/process/runs/any").status, 404)
    assertEquals(http.get("/process/answers/run?run=any").status, 404)
    val start = http.post(
      "/process/runs",
      Obj("blueprint" -> "appraiser", "runId" -> "no-process", "input" -> Obj())
    )
    assertEquals(start.status, 404, start.body)
  }
