package reasoning.process.api

import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.ViewClient
import reasoning.belief.api.Replies
import reasoning.belief.domain.{Refused, Rules}
import reasoning.process.api.ProcessJson.given
import reasoning.process.application.{ActRows, ProcessRecorder}
import reasoning.process.domain.RecordRef

/** Acts: one by its id, or the one of a record. */
final class ActsEndpoint(recorder: ProcessRecorder, views: ViewClient, val acl: Acl)
    extends HttpEndpoint("/process/acts"):

  get("/") { () =>
    Replies.handle {
      val text = query
        .optional[String]("record")
        .getOrElse(throw Refused("record.format", 400, "'record' is '<kind>:<id>'."))
      val record = text.split(':') match
        case Array(kind, id) if RecordRef.kinds.contains(kind) => RecordRef(kind, id)
        case _ =>
          throw Refused(
            "record.format",
            400,
            "'record' is 'evidence:<id>', 'claim:<id>' or 'revision:<id>'.",
            "record" -> text
          )
      Replies.ok(ActView(ActRows.byRecord(views, record).flatMap(row => recorder.findAct(row.id))))
    }
  }

  get("/{act}") { (id: String) =>
    Replies.handle(Replies.ok(recorder.findAct(id).getOrElse(throw Rules.notHeld("act", id))))
  }
