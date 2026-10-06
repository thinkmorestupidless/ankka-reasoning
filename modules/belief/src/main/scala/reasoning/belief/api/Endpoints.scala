package reasoning.belief.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.SqlSyntax.{jsonText, sql}
import com.thinkmorestupidless.ankka.runtime.ViewClient
import reasoning.belief.application.*
import reasoning.belief.domain.*

/** The JSON of everything the belief layer's routes read and write. */
object BeliefJson:
  given JsonValueCodec[Question]         = Replies.codec[Question]
  given JsonValueCodec[Holder]           = Replies.codec[Holder]
  given JsonValueCodec[Source]           = Replies.codec[Source]
  given JsonValueCodec[Evidence]         = Replies.codec[Evidence]
  given JsonValueCodec[Claim]            = Replies.codec[Claim]
  given JsonValueCodec[Revision]         = Replies.codec[Revision]
  given JsonValueCodec[BeliefHead]       = Replies.codec[BeliefHead]
  given JsonValueCodec[LinePage]         = Replies.codec[LinePage]
  given JsonValueCodec[OpenQuestion]     = Replies.codec[OpenQuestion]
  given JsonValueCodec[AddHypothesis]    = Replies.codec[AddHypothesis]
  given JsonValueCodec[RegisterHolder]   = Replies.codec[RegisterHolder]
  given JsonValueCodec[AddWriterRequest] = Replies.codec[AddWriterRequest]
  given JsonValueCodec[RegisterSource]   = Replies.codec[RegisterSource]
  given JsonValueCodec[RecordEvidence]   = Replies.codec[RecordEvidence]
  given JsonValueCodec[StateClaim]       = Replies.codec[StateClaim]
  given JsonValueCodec[StateBelief]      = Replies.codec[StateBelief]
  given JsonValueCodec[ClaimRead]        = Replies.codec[ClaimRead]
  given JsonValueCodec[Withdraw]         = Replies.codec[Withdraw]

import BeliefJson.given

/**
 * Every endpoint takes its ACL from the service, which is where a caller becomes a writer. The
 * writer is the authenticated principal's subject and is never read from a request's body.
 */
final class QuestionsEndpoint(recorder: Recorder, val acl: Acl) extends HttpEndpoint("/questions"):

  postBody("/") { (request: OpenQuestion) =>
    Replies.handle {
      val recorded = recorder.openQuestion(request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  postBody("/{question}/hypotheses") { (question: String, request: AddHypothesis) =>
    Replies.handle {
      val recorded = recorder.addHypothesis(question, request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  get("/{question}")((question: String) => Replies.handle(Replies.ok(recorder.question(question))))

final class HoldersEndpoint(recorder: Recorder, val acl: Acl) extends HttpEndpoint("/holders"):

  postBody("/") { (request: RegisterHolder) =>
    Replies.handle {
      val recorded = recorder.registerHolder(request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  postBody("/{holder}/writers") { (holder: String, request: AddWriterRequest) =>
    Replies.handle {
      val recorded = recorder.addWriter(holder, request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  get("/{holder}")((holder: String) => Replies.handle(Replies.ok(recorder.holder(holder))))

final class SourcesEndpoint(recorder: Recorder, val acl: Acl) extends HttpEndpoint("/sources"):

  postBody("/") { (request: RegisterSource) =>
    Replies.handle {
      val recorded = recorder.registerSource(request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  get("/{source}")((source: String) => Replies.handle(Replies.ok(recorder.source(source))))

final class EvidenceEndpoint(recorder: Recorder, val acl: Acl) extends HttpEndpoint("/evidence"):

  postBody("/") { (request: RecordEvidence) =>
    Replies.handle {
      val recorded = recorder.recordEvidence(request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  get("/{evidence}")((evidence: String) => Replies.handle(Replies.ok(recorder.evidence(evidence))))

  // The one way a held record changes: its text is taken out, and everything else stays.
  postBody("/{evidence}/withdrawal") { (evidence: String, request: Withdraw) =>
    Replies.handle(
      Replies.ok(recorder.withdrawEvidence(evidence, request, principal.subject).record)
    )
  }

/** A claim as it is read back: the claim, and the claims that revise it. */
final case class ClaimRead(claim: Claim, revisedBy: Vector[String])

final class ClaimsEndpoint(recorder: Recorder, views: ViewClient, val acl: Acl)
    extends HttpEndpoint("/claims"):

  private val rows = views.forView(ClaimRows)

  postBody("/") { (request: StateClaim) =>
    Replies.handle {
      val recorded = recorder.stateClaim(request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  postBody("/{claim}/withdrawal") { (claim: String, request: Withdraw) =>
    Replies.handle(Replies.ok(recorder.withdrawClaim(claim, request, principal.subject).record))
  }

  // `revisedBy` comes from a view, so it may lag a write by a moment. Nothing is decided from it.
  get("/{claim}") { (claim: String) =>
    Replies.handle {
      val held = recorder.claim(claim)
      val revisedBy =
        rows.where(jsonText("revises") ++ sql" = ${held.id}").sortBy(_.dated).map(_.id)
      Replies.ok(ClaimRead(held, revisedBy))
    }
  }

final class BeliefsEndpoint(recorder: Recorder, val acl: Acl) extends HttpEndpoint("/beliefs"):

  postBody("/revisions") { (request: StateBelief) =>
    Replies.handle {
      val recorded = recorder.stateBelief(request, principal.subject)
      Replies.recorded(recorded.record, recorded.created)
    }
  }

  get("/revisions/{revision}") { (revision: String) =>
    Replies.handle(Replies.ok(recorder.revision(revision)))
  }

  get("/current") { () =>
    Replies.handle(
      Replies.ok(
        recorder.belief(query.required[String]("holder"), query.required[String]("hypothesis"))
      )
    )
  }

  get("/line") { () =>
    Replies.handle {
      Replies.ok(
        recorder.line(
          query.required[String]("holder"),
          query.required[String]("hypothesis"),
          query.optional[String]("before"),
          query.optional[Int]("limit").getOrElse(50)
        )
      )
    }
  }
