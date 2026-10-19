package reasoning.process.domain

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import reasoning.belief.domain.Moment

/**
 * The records of the process layer: what this service holds of ankka's blueprint versions and runs,
 * and the act that ties each record a run wrote to the run.
 *
 * None carries text a writer supplied. A run ending's reason and an act's step and worker are
 * ankka's words and a blueprint's names. None is changed once held, and none is withdrawn.
 */

/** One version of a blueprint, as ankka registered it. Its id is `<blueprint>@<version>`. */
final case class BlueprintVersion(
    id: String,
    blueprint: String,
    version: Int,
    digest: String,
    dated: Moment,
    recordedAt: Moment,
    writer: String
)

/** One run of a blueprint version, as ankka started it. A run with no ending is running. */
final case class Run(
    id: String,
    blueprint: String,
    version: Int,
    startedBy: String,
    dated: Moment,
    recordedAt: Moment,
    writer: String
)

/** How a run ended. Held once, under the run's id. */
final case class RunEnding(
    run: String,
    status: RunStatus,
    reason: Option[String],
    dated: Moment,
    recordedAt: Moment,
    writer: String
)

/** A record of the belief layer, by its kind and its id. */
final case class RecordRef(kind: String, id: String)

object RecordRef:
  val Evidence = "evidence"
  val Claim    = "claim"
  val Revision = "revision"
  val kinds    = Set(Evidence, Claim, Revision)

/**
 * That a run wrote a record: in which step, by which worker of which version, as which holder. It
 * is dated as the record it names, and written by the blueprint.
 */
final case class Act(
    id: String,
    run: String,
    step: String,
    worker: String,
    blueprint: String,
    version: Int,
    record: RecordRef,
    holder: String,
    dated: Moment,
    recordedAt: Moment,
    writer: String
)

/** How a run ended: ankka's status, less those of a run that has not. */
enum RunStatus(val wire: String):
  case Completed extends RunStatus("completed")
  case Failed    extends RunStatus("failed")
  case Cancelled extends RunStatus("cancelled")

object RunStatus:

  def fromWire(text: String): Option[RunStatus] = values.find(_.wire == text)

  /** ankka's status, or `None` for a run running or waiting for a decision, which has not ended. */
  def fromAnkka(
      status: com.thinkmorestupidless.ankka.agent.blueprint.RunStatus
  ): Option[RunStatus] =
    fromWire(status.wire)

  /** Written as its name, as ankka writes its own. */
  given JsonValueCodec[RunStatus] = new JsonValueCodec[RunStatus]:
    def decodeValue(in: JsonReader, default: RunStatus): RunStatus =
      val text = in.readString(null)
      fromWire(text).getOrElse(in.decodeError(s"not a run status: '$text'"))
    def encodeValue(x: RunStatus, out: JsonWriter): Unit = out.writeVal(x.wire)
    def nullValue: RunStatus                             = null
