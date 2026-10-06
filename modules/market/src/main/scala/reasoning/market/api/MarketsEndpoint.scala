package reasoning.market.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.http.*
import reasoning.belief.answers.At
import reasoning.belief.api.Replies
import reasoning.belief.domain.{Moment, Refused}
import reasoning.market.answers.*
import reasoning.market.application.*
import reasoning.market.domain.Market

object MarketJson:
  given JsonValueCodec[Market]              = Replies.codec[Market]
  given JsonValueCodec[OpenMarket]          = Replies.codec[OpenMarket]
  given JsonValueCodec[ObservePrice]        = Replies.codec[ObservePrice]
  given JsonValueCodec[ResolveMarket]       = Replies.codec[ResolveMarket]
  given JsonValueCodec[PriceRecorded]       = Replies.codec[PriceRecorded]
  given JsonValueCodec[WhyResolved]         = Replies.codec[WhyResolved]
  given JsonValueCodec[BeliefsAtResolution] = Replies.codec[BeliefsAtResolution]

import MarketJson.given

/** The market layer's routes: a market, its price observations and its resolutions. */
final class MarketsEndpoint(recorder: MarketRecorder, answers: MarketAnswers, val acl: Acl)
    extends HttpEndpoint("/markets"):

  private def moment(name: String): Option[Moment] =
    query.optional[String](name).map { text =>
      Moment
        .parse(text)
        .getOrElse(
          throw Refused("time.format", 422, s"'$name' is not an ISO-8601 instant.", name -> text)
        )
    }

  private def at: At = At.of(moment("asOf"), moment("asRecordedBy"))

  postBody("/") { (request: OpenMarket) =>
    Replies.handle {
      val recorded = recorder.open(request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  get("/{market}")((market: String) => Replies.handle(Replies.ok(recorder.market(market))))

  postBody("/{market}/price-observations") { (market: String, request: ObservePrice) =>
    Replies.handle {
      val recorded = recorder.observe(market, request, principal.subject)
      Replies.recorded(recorded, recorded.added)
    }
  }

  postBody("/{market}/resolutions") { (market: String, request: ResolveMarket) =>
    Replies.handle {
      val recorded = recorder.resolve(market, request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  get("/{market}/resolution") { (market: String) =>
    Replies.handle(Replies.ok(answers.whyResolved(market, at)))
  }

  get("/{market}/beliefs-at-resolution") { (market: String) =>
    Replies.handle(Replies.ok(answers.beliefsAtResolution(market, at)))
  }
