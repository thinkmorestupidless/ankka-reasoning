package reasoning.belief.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.http.*
import reasoning.belief.answers.*
import reasoning.belief.domain.{Moment, Refused}

/** The JSON of the belief layer's answers. */
object AnswersJson:
  given JsonValueCodec[BeliefChange]     = Replies.codec[BeliefChange]
  given JsonValueCodec[BeliefAnswer]     = Replies.codec[BeliefAnswer]
  given JsonValueCodec[CaseAnswer]       = Replies.codec[CaseAnswer]
  given JsonValueCodec[Learned]          = Replies.codec[Learned]
  given JsonValueCodec[Comparison]       = Replies.codec[Comparison]
  given JsonValueCodec[RestingOnRevised] = Replies.codec[RestingOnRevised]

import AnswersJson.given

/**
 * The answers, each read from the graph database and made of records only. Every route takes `asOf`
 * and `asRecordedBy`, and answers 503 when the graph database cannot be read.
 */
final class AnswersEndpoint(answers: BeliefAnswers, val acl: Acl) extends HttpEndpoint("/answers"):

  private def moment(name: String): Option[Moment] =
    query.optional[String](name).map { text =>
      Moment
        .parse(text)
        .getOrElse(
          throw Refused("time.format", 422, s"'$name' is not an ISO-8601 instant.", name -> text)
        )
    }

  private def required(name: String): String = query.required[String](name)

  private def at: At = At.of(moment("asOf"), moment("asRecordedBy"))

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

  // Two holders' beliefs in one hypothesis. A second hypothesis may be named for the second
  // holder only to be told that beliefs in two hypotheses are not compared.
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
