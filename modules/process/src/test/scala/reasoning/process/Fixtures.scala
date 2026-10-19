package reasoning.process

import reasoning.belief.domain.Moment
import reasoning.process.domain.*

/** Records of the process layer as the suites use them. */
object Fixtures:

  val t1: Moment = Moment(1_000L)
  val t2: Moment = Moment(2_000L)
  val t3: Moment = Moment(3_000L)

  val version: BlueprintVersion =
    BlueprintVersion("appraiser@1", "appraiser", 1, "d" * 64, t1, t1, "service:reasoning")

  val run: Run = Run("r1", "appraiser", 1, "service:shop/caller", t2, t2, "service:reasoning")

  val ending: RunEnding =
    RunEnding("r1", RunStatus.Failed, Some("verdict: claim r1.x does not derive"), t3, t3, "s")

  val evidence: RecordRef = RecordRef(RecordRef.Evidence, "e" * 64)

  val act: Act =
    Act(
      Ids.act(evidence),
      "r1",
      "read",
      "reader",
      "appraiser",
      1,
      evidence,
      "appraiser-a",
      t2,
      t3,
      "blueprint:appraiser"
    )
