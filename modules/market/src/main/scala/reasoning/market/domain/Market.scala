package reasoning.market.domain

import reasoning.belief.domain.{Moment, Refusal}

/** An outcome a market offers, for one hypothesis of its question. */
final case class Offer(outcome: String, hypothesis: String)

/**
 * Which outcome a market came to, on what evidence and on whose authority. With no outcome it is
 * void: the market could not be decided. A later resolution may revise it, and both are kept.
 */
final case class Resolution(
    id: String,
    outcome: Option[String],
    evidence: Vector[String],
    authority: String,
    revises: Option[String],
    dated: Moment,
    recordedAt: Moment,
    writer: String
):
  def void: Boolean = outcome.isEmpty

/**
 * A place where a question is traded. It is about one question, offers an outcome for each
 * hypothesis it trades, and speaks as a holder whose belief revisions are its price observations.
 */
final case class Market(
    id: String,
    question: String,
    venue: String,
    outcomes: Vector[Offer],
    resolutionCriteria: String,
    closesAt: Moment,
    holder: String,
    resolutions: Vector[Resolution],
    dated: Moment,
    recordedAt: Moment,
    writer: String
):
  def offer(outcome: String): Option[Offer] = outcomes.find(_.outcome == outcome)

  /** The resolution no later one revises. */
  def current: Option[Resolution] = resolutions.lastOption

object Market:
  val IdLimit      = 32
  val OutcomeLimit = 16

  /** The holder a market speaks as. */
  def holderOf(market: String): String = s"market.$market"

  /** The identifier of the belief revision one price observation is. */
  def observation(market: String, outcome: String, observedAt: Moment): String =
    s"$market.$outcome.${observedAt.millis}"

/** Whether a market or a resolution sent again is the one held. */
object SameMarket:

  def market(held: Market, sent: Market, datedStated: Boolean): Boolean =
    held.question == sent.question &&
      held.venue == sent.venue &&
      held.outcomes == sent.outcomes &&
      held.resolutionCriteria == sent.resolutionCriteria &&
      held.closesAt == sent.closesAt &&
      (!datedStated || held.dated == sent.dated)

  def resolution(held: Resolution, sent: Resolution, datedStated: Boolean): Boolean =
    held.outcome == sent.outcome &&
      held.evidence.toSet == sent.evidence.toSet &&
      held.authority == sent.authority &&
      held.revises == sent.revises &&
      (!datedStated || held.dated == sent.dated)

/** Every rule of the market layer, by the names of the refusals contract. */
object MarketRules:

  val VenueLimit     = 200
  val CriteriaLimit  = 2000
  val AuthorityLimit = 200

  private def refusal(
      rule: String,
      status: Int,
      error: String,
      names: (String, String)*
  ): Option[Refusal] =
    Some(Refusal(rule, status, error, names.toMap))

  def questionNotHeld(market: String, question: String): Refusal =
    Refusal(
      "market.question.not-held",
      422,
      s"No question '$question' is held.",
      Map("market" -> market, "question" -> question)
    )

  def notHeld(market: String): Refusal =
    Refusal("market.not-held", 422, s"No market '$market' is held.", Map("market" -> market))

  def someOutcome(market: String, outcomes: Vector[Offer]): Option[Refusal] =
    if outcomes.nonEmpty then None
    else
      refusal(
        "market.outcome.none",
        422,
        "A market offers at least one outcome.",
        "market" -> market
      )

  def outcomeNamesOnce(market: String, outcomes: Vector[Offer]): Option[Refusal] =
    outcomes.groupBy(_.outcome).collectFirst { case (name, all) if all.sizeIs > 1 => name }.flatMap {
      name =>
        refusal(
          "market.outcome.name-twice",
          422,
          "Two outcomes of a market cannot have one name.",
          "market"  -> market,
          "outcome" -> name
        )
    }

  def hypothesesOnce(market: String, outcomes: Vector[Offer]): Option[Refusal] =
    outcomes
      .groupBy(_.hypothesis)
      .collectFirst { case (hypothesis, all) if all.sizeIs > 1 => hypothesis }
      .flatMap { hypothesis =>
        refusal(
          "market.outcome.hypothesis-twice",
          422,
          "Two outcomes of a market cannot be for one hypothesis.",
          "market"     -> market,
          "hypothesis" -> hypothesis
        )
      }

  def hypothesisOfQuestion(market: String, question: String, offer: Offer): Refusal =
    Refusal(
      "market.outcome.hypothesis-of-other-question",
      422,
      "An outcome is for a hypothesis of the market's own question.",
      Map(
        "market"     -> market,
        "question"   -> question,
        "outcome"    -> offer.outcome,
        "hypothesis" -> offer.hypothesis
      )
    )

  def datedNotBeforeHypothesis(
      market: String,
      dated: Moment,
      hypothesis: String,
      hypothesisDated: Moment
  ): Option[Refusal] =
    if dated >= hypothesisDated then None
    else
      refusal(
        "market.dated.not-before-hypothesis",
        422,
        "A market cannot be dated before a hypothesis it offers an outcome for.",
        "market"     -> market,
        "hypothesis" -> hypothesis,
        "dated"      -> dated.iso
      )

  def outcomeNotOffered(market: String, outcome: String): Refusal =
    Refusal(
      "market.outcome.not-offered",
      422,
      s"The market does not offer the outcome '$outcome'.",
      Map("market" -> market, "outcome" -> outcome)
    )

  def price(market: String, value: Double): Option[Refusal] =
    if value >= 0.0 && value <= 1.0 then None
    else
      refusal(
        "market.price.range",
        422,
        "A price is from nought to one.",
        "market" -> market,
        "price"  -> value.toString
      )

  def beforeClose(market: Market, observedAt: Moment): Option[Refusal] =
    if observedAt <= market.closesAt then None
    else
      refusal(
        "market.price.after-close",
        422,
        "A market takes no price observation dated later than its closing time.",
        "market"     -> market.id,
        "closesAt"   -> market.closesAt.iso,
        "observedAt" -> observedAt.iso
      )

  def someEvidence(market: String, resolution: String, evidence: Vector[String]): Option[Refusal] =
    if evidence.nonEmpty then None
    else
      refusal(
        "market.resolution.evidence.none",
        422,
        "A resolution names at least one piece of evidence.",
        "market"     -> market,
        "resolution" -> resolution
      )

  def evidenceNotHeld(market: String, resolution: String, evidence: String): Refusal =
    Refusal(
      "market.resolution.evidence.not-held",
      422,
      s"No evidence '$evidence' is held.",
      Map("market" -> market, "resolution" -> resolution, "evidence" -> evidence)
    )

  def resolutionNotBeforeEvidence(
      market: String,
      resolution: String,
      dated: Moment,
      evidence: String,
      evidenceDated: Moment
  ): Option[Refusal] =
    if dated >= evidenceDated then None
    else
      refusal(
        "market.resolution.dated.not-before-evidence",
        422,
        "A resolution cannot be dated before evidence it rests on.",
        "market"     -> market,
        "resolution" -> resolution,
        "evidence"   -> evidence,
        "dated"      -> dated.iso
      )

  def resolutionNotBeforeMarket(
      market: Market,
      resolution: String,
      dated: Moment
  ): Option[Refusal] =
    if dated >= market.dated then None
    else
      refusal(
        "market.resolution.dated.not-before-market",
        422,
        "A resolution cannot be dated before its market.",
        "market"     -> market.id,
        "resolution" -> resolution,
        "dated"      -> dated.iso
      )

  def revisesCurrent(market: Market, resolution: String, revises: Option[String]): Option[Refusal] =
    if revises == market.current.map(_.id) then None
    else
      refusal(
        "market.resolution.revises.not-current",
        409,
        market.current.fold(
          "The market has no resolution yet, so a resolution of it revises none."
        )(c => s"A resolution revises the market's current resolution, which is '${c.id}'."),
        (Seq("market" -> market.id, "resolution" -> resolution) ++ market.current.map(c =>
          "current" -> c.id
        ) ++ revises.map("revises" -> _))*
      )
