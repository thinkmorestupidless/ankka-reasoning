package reasoning.market.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.core.Serializers.given
import com.thinkmorestupidless.ankka.sdk.*
import reasoning.belief.application.{Put, Slot}
import reasoning.belief.domain.{Outcome, Rules}
import reasoning.market.domain.*

/**
 * One market, with its resolutions. It is a key value entity for the reason every record with text
 * is, and it holds its resolutions itself because one entity is what keeps them in one line.
 */
final class MarketEntity(context: KeyValueEntityContext) extends KeyValueEntity[Slot[Market]]:

  private val id: String = context.entityId

  def emptyState: Slot[Market] = Slot(None)

  def open(put: Put[Market]): Effect[Outcome[Market]] = currentState.held match
    case None =>
      effects.updateState(Slot(Some(put.record))).thenReply(_ => Outcome.created(put.record))
    case Some(held) if SameMarket.market(held, put.record, put.datedStated) =>
      effects.reply(Outcome.repeat(held))
    case Some(_) => effects.reply(Outcome.refused(Rules.heldByAnother("market", id)))

  def resolve(put: Put[Resolution]): Effect[Outcome[Market]] = currentState.held match
    case None => effects.reply(Outcome.refused(MarketRules.notHeld(id)))
    case Some(market) =>
      val sent = put.record
      market.resolutions.find(_.id == sent.id) match
        case Some(held) if SameMarket.resolution(held, sent, put.datedStated) =>
          effects.reply(Outcome.repeat(market))
        case Some(_) =>
          effects.reply(Outcome.refused(Rules.heldByAnother("resolution", s"$id/${sent.id}")))
        case None =>
          MarketRules
            .revisesCurrent(market, sent.id, sent.revises)
            .orElse(MarketRules.resolutionNotBeforeMarket(market, sent.id, sent.dated)) match
            case Some(refusal) => effects.reply(Outcome.refused(refusal))
            case None =>
              val resolved = market.copy(resolutions = market.resolutions :+ sent)
              effects.updateState(Slot(Some(resolved))).thenReply(_ => Outcome.created(resolved))

  def get: ReadOnlyEffect[Slot[Market]] = effects.reply(currentState)

  /** The market's revision, which is the version its elements are published at. */
  def version: ReadOnlyEffect[Long] = effects.reply(commandContext.sequenceNumber)

object MarketEntity
    extends KeyValueEntity.Companion[MarketEntity, Slot[Market]](
      componentId = ComponentId("market"),
      stateSerializer = Codecs.serializer[Slot[Market]]("market")
    ):
  given putMarket: Serializer[Put[Market]] = Codecs.serializer[Put[Market]]("market-put")
  given putResolution: Serializer[Put[Resolution]] =
    Codecs.serializer[Put[Resolution]]("resolution-put")
  given outcome: Serializer[Outcome[Market]] = Codecs.serializer[Outcome[Market]]("market-outcome")

  def create(context: KeyValueEntityContext) = new MarketEntity(context)

  val open    = command("open")(_.open)
  val resolve = command("resolve")(_.resolve)
  val get     = query("get")(_.get)
  val version = query("version")(_.version)
