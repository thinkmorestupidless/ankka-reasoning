package reasoning.market.application

import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import reasoning.belief.application.*
import reasoning.belief.domain.*
import reasoning.graph.Element
import reasoning.market.MarketVocabulary
import reasoning.market.domain.*

final case class OpenMarket(
    id: String,
    question: String,
    venue: String,
    outcomes: Vector[Offer],
    resolutionCriteria: String,
    closesAt: Moment,
    dated: Option[Moment] = None
)

final case class ObservePrice(outcome: String, price: Double, observedAt: Option[Moment] = None)

final case class ResolveMarket(
    id: String,
    outcome: Option[String] = None,
    evidence: Vector[String] = Vector.empty,
    authority: String,
    revises: Option[String] = None,
    dated: Option[Moment] = None
)

/** The market's current revision for an outcome, and whether this observation added it. */
final case class PriceRecorded(revision: Revision, added: Boolean)

/**
 * Takes the market layer's records from a writer. It writes through the belief layer and adds
 * nothing to it: a market's holder is registered like any holder, and a price observation is a
 * revision of that holder's belief, stated like any other.
 */
final class MarketRecorder(client: ComponentClient, clock: Clock, belief: Recorder):

  def findMarket(id: String): Option[Market] =
    if !Ids.valid(id, Market.IdLimit) then None
    else client.forKeyValueEntity(EntityId(id)).call(MarketEntity.get).invoke().held

  def market(id: String): Market = findMarket(id).getOrElse(throw Rules.notHeld("market", id))

  private def speaksFor(market: Market, writer: String): Unit =
    val holder = belief.holder(market.holder)
    if !holder.spokenForBy(writer) then
      throw Refused(
        "holder.writer.does-not-speak",
        403,
        s"The writer does not speak for the market '${market.id}'.",
        "market" -> market.id,
        "holder" -> holder.id,
        "writer" -> writer
      )

  /** A market's elements and the version they are published at, for whoever waits for the graph. */
  def published(kind: String, id: String): Option[(Vector[Element], Long)] = kind match
    case "market" =>
      findMarket(id).map(m =>
        MarketGraphOf
          .market(m) -> client.forKeyValueEntity(EntityId(id)).call(MarketEntity.version).invoke()
      )
    case _ => None

  /**
   * Opens a market: registers the holder it speaks as, then creates it. Both are safe to repeat, so
   * a failure between them is mended by sending the market again.
   */
  def open(request: OpenMarket, writer: String): Recorded[Market] =
    val now   = clock.now()
    val dated = request.dated.getOrElse(now)
    val id    = request.id
    Rules.require(
      (Seq(
        Rules.id("market", id, Market.IdLimit),
        Rules.text("market", id, "venue", request.venue, MarketRules.VenueLimit),
        Rules.text(
          "market",
          id,
          "resolutionCriteria",
          request.resolutionCriteria,
          MarketRules.CriteriaLimit
        ),
        MarketRules.someOutcome(id, request.outcomes)
      ) ++ request.outcomes.map(o => Rules.id("outcome", o.outcome, Market.OutcomeLimit)) ++ Seq(
        MarketRules.outcomeNamesOnce(id, request.outcomes),
        MarketRules.hypothesesOnce(id, request.outcomes),
        Rules.notLaterThanNow("market", id, dated, now)
      ))*
    )
    val question = belief
      .findQuestion(request.question)
      .getOrElse(throw new Refused(MarketRules.questionNotHeld(id, request.question)))
    request.outcomes.foreach { offer =>
      val hypothesis = HypothesisRef
        .parse(offer.hypothesis)
        .filter(_.question == question.id)
        .flatMap(ref => question.hypothesis(ref.hypothesis))
        .getOrElse(throw new Refused(MarketRules.hypothesisOfQuestion(id, question.id, offer)))
      Rules.require(
        MarketRules.datedNotBeforeHypothesis(id, dated, offer.hypothesis, hypothesis.dated)
      )
    }
    val holder = Market.holderOf(id)
    belief.registerHolder(
      RegisterHolder(holder, MarketVocabulary.HolderKindName, request.venue),
      writer
    ): Unit
    val record = Market(
      id,
      request.question,
      request.venue,
      request.outcomes,
      request.resolutionCriteria,
      request.closesAt,
      holder,
      Vector.empty,
      dated,
      now,
      writer
    )
    client
      .forKeyValueEntity(EntityId(id))
      .call(MarketEntity.open)
      .invoke(Put(record, request.dated.isDefined))
      .recorded

  /**
   * A price observation: the market's belief revision in that outcome's hypothesis, resting on no
   * claims. One equal to the current one adds nothing.
   */
  def observe(marketId: String, request: ObservePrice, writer: String): PriceRecorded =
    val now      = clock.now()
    val market   = this.market(marketId)
    val observed = request.observedAt.getOrElse(now)
    speaksFor(market, writer)
    val offer = market
      .offer(request.outcome)
      .getOrElse(throw new Refused(MarketRules.outcomeNotOffered(marketId, request.outcome)))
    Rules.require(
      MarketRules.price(marketId, request.price),
      Rules.notLaterThanNow("price observation", marketId, observed, now),
      MarketRules.beforeClose(market, observed)
    )
    val id = Market.observation(marketId, request.outcome, observed)
    // The same observation sent again: its identifier is the outcome and the time it was observed.
    belief.findRevision(id) match
      case Some(held) if held.probability == request.price =>
        return PriceRecorded(held, added = false)
      case Some(_) => throw new Refused(Rules.heldByAnother("price observation", id))
      case None    => ()
    val current = belief.belief(market.holder, offer.hypothesis).current
    val recorded = belief.stateBelief(
      StateBelief(
        id,
        market.holder,
        offer.hypothesis,
        request.price,
        Vector.empty,
        current.map(_.id),
        Some(observed)
      ),
      writer,
      unlessUnchanged = true
    )
    PriceRecorded(recorded.record, recorded.created)

  /** Resolves a market to one of its outcomes, or as void, on evidence and an authority. */
  def resolve(marketId: String, request: ResolveMarket, writer: String): Recorded[Market] =
    val now    = clock.now()
    val dated  = request.dated.getOrElse(now)
    val market = this.market(marketId)
    speaksFor(market, writer)
    Rules.require(
      Rules.id("resolution", request.id),
      Rules.text(
        "resolution",
        s"$marketId/${request.id}",
        "authority",
        request.authority,
        MarketRules.AuthorityLimit
      ),
      MarketRules.someEvidence(marketId, request.id, request.evidence),
      Rules.notLaterThanNow("resolution", s"$marketId/${request.id}", dated, now)
    )
    request.outcome.foreach { outcome =>
      if market.offer(outcome).isEmpty then
        throw new Refused(MarketRules.outcomeNotOffered(marketId, outcome))
    }
    request.evidence.foreach { evidenceId =>
      val evidence = belief
        .findEvidence(evidenceId)
        .getOrElse(throw new Refused(MarketRules.evidenceNotHeld(marketId, request.id, evidenceId)))
      Rules.require(
        MarketRules.resolutionNotBeforeEvidence(
          marketId,
          request.id,
          dated,
          evidenceId,
          evidence.dated
        )
      )
    }
    val resolution = Resolution(
      request.id,
      request.outcome,
      request.evidence,
      request.authority,
      request.revises,
      dated,
      now,
      writer
    )
    client
      .forKeyValueEntity(EntityId(marketId))
      .call(MarketEntity.resolve)
      .invoke(Put(resolution, request.dated.isDefined))
      .recorded
