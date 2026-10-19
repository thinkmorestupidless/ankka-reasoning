package reasoning.process.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.http.*
import reasoning.belief.answers.*
import reasoning.belief.api.Replies
import reasoning.belief.domain.{Moment, Refused}
import reasoning.market.answers.{BeliefsAtResolution, WhyResolved}
import reasoning.process.answers.*

/** The JSON of the answers with acts: the lower answer's own codec, nested, so it is unchanged. */
object ProcessAnswersJson:
  given beliefChange: JsonValueCodec[WithActs[BeliefChange]]               = Replies.codec
  given belief: JsonValueCodec[WithActs[BeliefAnswer]]                     = Replies.codec
  given caseFor: JsonValueCodec[WithActs[CaseAnswer]]                      = Replies.codec
  given learned: JsonValueCodec[WithActs[Learned]]                         = Replies.codec
  given comparison: JsonValueCodec[WithActs[Comparison]]                   = Replies.codec
  given restingOnRevised: JsonValueCodec[WithActs[RestingOnRevised]]       = Replies.codec
  given whyResolved: JsonValueCodec[WithActs[WhyResolved]]                 = Replies.codec
  given beliefsAtResolution: JsonValueCodec[WithActs[BeliefsAtResolution]] = Replies.codec
  given runWrote: JsonValueCodec[RunWrote]                                 = Replies.codec

import ProcessAnswersJson.given

/** The parameters every answer route takes, read as the lower routes read them. */
abstract class AnswerRoutes(prefix: String) extends HttpEndpoint(prefix):

  protected def moment(name: String): Option[Moment] =
    query.optional[String](name).map { text =>
      Moment
        .parse(text)
        .getOrElse(
          throw Refused("time.format", 422, s"'$name' is not an ISO-8601 instant.", name -> text)
        )
    }

  protected def required(name: String): String = query.required[String](name)

  protected def at: At = At.of(moment("asOf"), moment("asRecordedBy"))

/**
 * The belief layer's answers again, each with the acts of the records in it, and everything a run
 * wrote. Every route takes `asOf` and `asRecordedBy`, and answers 503 when the graph database
 * cannot be read.
 */
final class ProcessAnswersEndpoint(answers: ProcessAnswers, val acl: Acl)
    extends AnswerRoutes("/process/answers"):

  get("/belief-change") { () =>
    Replies.handle(Replies.ok(answers.beliefChange(required("from"), required("to"), at)))
  }

  get("/belief") { () =>
    Replies.handle(Replies.ok(answers.belief(required("holder"), required("hypothesis"), at)))
  }

  get("/case") { () =>
    Replies.handle(Replies.ok(answers.caseFor(required("hypothesis"), required("stance"), at)))
  }

  get("/learned") { () =>
    Replies.handle {
      val after = moment("after").getOrElse(
        throw Refused("time.format", 422, "'after' is required.", "after" -> "")
      )
      Replies.ok(answers.learned(required("question"), after, at))
    }
  }

  // As the belief layer's: a second hypothesis may be named for the second holder only to be told
  // that beliefs in two hypotheses are not compared.
  get("/comparison") { () =>
    Replies.handle {
      val hypothesis = required("hypothesis")
      query.optional[String]("hypothesisOfB").filter(_ != hypothesis).foreach { other =>
        throw Refused(
          "answer.beliefs.in-two-hypotheses",
          422,
          "The two beliefs are in different hypotheses, and a comparison is of beliefs in one.",
          "hypothesis"    -> hypothesis,
          "hypothesisOfB" -> other
        )
      }
      Replies.ok(answers.comparison(hypothesis, required("a"), required("b"), at))
    }
  }

  get("/resting-on-revised") { () =>
    Replies.handle(Replies.ok(answers.restingOnRevised(required("question"), at)))
  }

  get("/run")(() => Replies.handle(Replies.ok(answers.run(required("run"), at))))

/** The market layer's answers again, each with the acts of the records in it. */
final class ProcessMarketAnswersEndpoint(answers: ProcessAnswers, val acl: Acl)
    extends AnswerRoutes("/process/markets"):

  get("/{market}/resolution") { (market: String) =>
    Replies.handle(Replies.ok(answers.whyResolved(market, at)))
  }

  get("/{market}/beliefs-at-resolution") { (market: String) =>
    Replies.handle(Replies.ok(answers.beliefsAtResolution(market, at)))
  }
