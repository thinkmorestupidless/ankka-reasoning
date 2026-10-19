package reasoning.process.application

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.runtime.ViewClient
import com.thinkmorestupidless.ankka.runtime.SqlSyntax.{jsonText, sql}
import com.thinkmorestupidless.ankka.sdk.*
import reasoning.belief.application.Slot
import reasoning.process.domain.{Act, RecordRef}

/** One row per act: identifiers and its date. */
final case class ActRow(
    id: String,
    run: String,
    recordKind: String,
    recordId: String,
    holder: String,
    dated: Long
)

/** A run's acts and a record's act, read back without the graph. Nothing is decided from it. */
final class ActRowsView extends View[Slot[Act], ActRow]:

  def onChange(state: Slot[Act]): Effect = state.held match
    case Some(act) =>
      effects.updateRow(
        ActRow(act.id, act.run, act.record.kind, act.record.id, act.holder, act.dated.millis)
      )
    case None => effects.ignore()

object ActRows
    extends View.Companion[ActRowsView, Slot[Act], ActRow](
      componentId = ComponentId("act-rows"),
      source = ChangeSource.stateOf(ActEntity),
      rowSerializer = Codecs.serializer[ActRow]("act-row")
    ):
  def create(ctx: ViewComponentContext) = new ActRowsView

  /** The acts of a run, oldest first. It may lag a write by a moment. */
  def byRun(views: ViewClient, run: String): Vector[ActRow] =
    views
      .forView(ActRows)
      .where(jsonText("run") ++ sql" = $run")
      .sortBy(r => (r.dated, r.id))
      .toVector

  /** The act of a record, when one is held. It may lag a write by a moment. */
  def byRecord(views: ViewClient, record: RecordRef): Option[ActRow] =
    views
      .forView(ActRows)
      .where(
        jsonText("recordKind") ++ sql" = ${record.kind} and " ++ jsonText("recordId") ++
          sql" = ${record.id}"
      )
      .headOption
