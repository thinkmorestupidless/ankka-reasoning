package reasoning.process

import reasoning.belief.domain.{HypothesisRef, Ids as BeliefIds}
import reasoning.process.domain.*

/** The ids a run's records are given fit the belief layer's, and name one record each. */
class IdsSuite extends munit.FunSuite:

  private val longest = "r" * Ids.RunIdLimit

  test("a claim of a run with the longest run id is a valid identifier of the belief layer") {
    val claim = Ids.claim(longest, "approval is pending")
    assertEquals(claim.length, BeliefIds.Limit)
    assert(BeliefIds.valid(claim), claim)
  }

  test("a claim's id is the run's and its statement's, and nothing else") {
    assertEquals(Ids.claim("r1", "a"), Ids.claim("r1", "a"))
    assertNotEquals(Ids.claim("r1", "a"), Ids.claim("r1", "b"))
    assertNotEquals(Ids.claim("r1", "a"), Ids.claim("r2", "a"))
  }

  test("a revision's id is the run's and its hypothesis's") {
    assertEquals(
      Ids.revision("appraiser-a-may", HypothesisRef("launch", "yes")),
      "appraiser-a-may.yes"
    )
  }

  test("a version's id is its blueprint's and its number") {
    assertEquals(Ids.version("appraiser", 2), "appraiser@2")
  }

  test("an act's id is its record's: one record, one act, whichever run met it") {
    val evidence = RecordRef(RecordRef.Evidence, "e1")
    assertEquals(Ids.act(evidence).length, 60)
    assert(Ids.act(evidence).startsWith("act:"))
    assertEquals(Ids.act(evidence), Ids.act(RecordRef(RecordRef.Evidence, "e1")))
    assertNotEquals(Ids.act(evidence), Ids.act(RecordRef(RecordRef.Claim, "e1")))
  }

  test("a run id longer than the limit, or one ankka keeps for schedules, is refused") {
    assertEquals(ProcessRules.runIdFormat(longest), None)
    assertEquals(ProcessRules.runIdFormat(longest + "r").map(_.rule), Some("run.id.too-long"))
    assertEquals(ProcessRules.runIdFormat("schedule:x").map(_.rule), Some("run.id.too-long"))
  }
