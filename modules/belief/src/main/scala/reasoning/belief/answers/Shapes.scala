package reasoning.belief.answers

import reasoning.belief.domain.Moment

/**
 * What an answer is made of: records as the graph holds them, and their identifiers. Every field is
 * a property of a node, an identifier, or a number worked out from two of them. No answer carries a
 * sentence the service wrote.
 */

final case class SourceView(id: String, name: String, dated: Moment, recordedAt: Moment)

/** Evidence with its source. Withdrawn, it has no locator, excerpt or author. */
final case class EvidenceView(
    id: String,
    source: Option[SourceView],
    locator: Option[String],
    excerpt: Option[String],
    author: Option[String],
    publishedAt: Option[Moment],
    dated: Moment,
    recordedAt: Moment,
    withdrawn: Boolean,
    withdrawnAt: Option[Moment]
)

/** A claim. Withdrawn, it has no statement. */
final case class ClaimView(
    id: String,
    holder: Option[String],
    statement: Option[String],
    dated: Moment,
    recordedAt: Moment,
    withdrawn: Boolean,
    withdrawnAt: Option[Moment]
)

/**
 * A claim as something a belief rests on or a case is made of: with the evidence it derives from
 * and where that came from, the claims that revise it, and the holders whose current belief
 * revisions rest on it.
 */
final case class Support(
    claim: ClaimView,
    weight: Option[Double],
    evidence: Vector[EvidenceView],
    revisedBy: Vector[String],
    heldBy: Vector[String]
)

final case class RevisionView(
    id: String,
    holder: String,
    hypothesis: String,
    n: Int,
    probability: Double,
    follows: Option[String],
    dated: Moment,
    recordedAt: Moment
)

/** A claim both revisions rest on, with the weight each gave it. */
final case class Still(support: Support, weightFrom: Option[Double], weightTo: Option[Double])

/** Why a belief changed between two of its revisions. */
final case class BeliefChange(
    from: RevisionView,
    to: RevisionView,
    newlyRestedOn: Vector[Support],
    noLongerRestedOn: Vector[Support],
    stillRestedOn: Vector[Still],
    observedBetween: Vector[EvidenceView]
)

/** A belief at a time: the revision that was current then, and what it rested on. */
final case class BeliefAnswer(revision: Option[RevisionView], restsOn: Vector[Support])

/** The claims taking one stance on a hypothesis, and apart from them the ones since revised. */
final case class CaseAnswer(claims: Vector[Support], revisedClaims: Vector[Support])

/** What was learned about a question after a time, oldest first. */
final case class Learned(
    evidence: Vector[EvidenceView],
    claims: Vector[ClaimView],
    revisions: Vector[RevisionView]
)

final case class Side(holder: String, revision: Option[RevisionView], statesNoReasons: Boolean)

/** A claim two holders both rest on, with the weight each gives it. */
final case class Shared(support: Support, weightA: Option[Double], weightB: Option[Double])

/**
 * Two holders' beliefs in one hypothesis, set side by side. `difference` is the gap between the two
 * probabilities, never negative.
 */
final case class Comparison(
    a: Side,
    b: Side,
    difference: Option[Double],
    both: Vector[Shared],
    onlyA: Vector[Support],
    onlyB: Vector[Support]
)

final case class RestingBelief(
    holder: String,
    hypothesis: String,
    revision: RevisionView,
    revisedClaims: Vector[Support]
)

/**
 * The beliefs about a question whose current revision rests on a claim that has since been revised.
 */
final case class RestingOnRevised(beliefs: Vector[RestingBelief])
