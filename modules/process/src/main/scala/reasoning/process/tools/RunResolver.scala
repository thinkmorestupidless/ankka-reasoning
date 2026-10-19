package reasoning.process.tools

import com.thinkmorestupidless.ankka.agent.Json
import com.thinkmorestupidless.ankka.agent.blueprint.{Blueprint, RunContext, RunRef, RunSnapshot}
import reasoning.belief.domain.{Moment, Refused, Rules}
import reasoning.process.application.ProcessRecorder
import reasoning.process.domain.*

import scala.collection.concurrent.TrieMap

/**
 * What a run of the appraiser is given: its tranche, its holder, its question, and perhaps more.
 */
final case class RunInput(
    tranche: String,
    holder: String,
    question: String,
    follows: Option[String],
    observedAt: Option[Moment]
)

object RunInput:

  /** Reads a run's input, refusing `run.input.invalid` naming the first field that does not fit. */
  def parse(input: Json): RunInput =
    def text(field: String): Option[String] = input(field) match
      case Some(Json.Str(value))  => Some(value)
      case Some(Json.Null) | None => None
      case Some(_) => throw new Refused(ProcessRules.inputInvalid(field, "must be a string"))
    def required(field: String): String =
      text(field).getOrElse(throw new Refused(ProcessRules.inputInvalid(field, "is required")))
    RunInput(
      required("tranche"),
      required("holder"),
      required("question"),
      text("follows"),
      text("observedAt").map(iso =>
        Moment
          .parse(iso)
          .getOrElse(
            throw new Refused(ProcessRules.inputInvalid("observedAt", "is not an ISO-8601 instant"))
          )
      )
    )

/** The run a tool serves, resolved: held, of a version held, in a step with one worker. */
final case class Resolved(
    ref: RunRef,
    run: Run,
    version: BlueprintVersion,
    worker: String,
    input: RunInput
)

/**
 * Finds the run a tool is called in, from ankka's context, and holds the version and the run when
 * the service has not followed them yet: a tool may run before the run follower sees the start.
 * What ankka says of a run and a version never changes, so each is read once per process.
 */
final class RunResolver(reads: RunReads, recorder: ProcessRecorder, serviceWriter: String):

  private val runs       = TrieMap.empty[String, RunSnapshot]
  private val blueprints = TrieMap.empty[(String, Int), Blueprint]

  /** ankka's record of a run, read once; `run.not-held` when ankka holds none. */
  def snapshot(runId: String): RunSnapshot =
    runs.getOrElseUpdate(
      runId,
      reads.run(runId).getOrElse {
        Rules.require(ProcessRules.runNotHeld(runId, None))
        throw IllegalStateException("unreachable")
      }
    )

  def resolve(tool: String): Resolved =
    val ref = RunContext.current.getOrElse(throw new Refused(ProcessRules.toolCalledInNoRun(tool)))
    val snapshot = this.snapshot(ref.runId)
    val version  = hold(snapshot)
    val run      = recorder.findRun(ref.runId).getOrElse(holdRun(snapshot, ref.runId))
    val blueprint = blueprints.getOrElseUpdate(
      (ref.blueprint, ref.version),
      reads.version(ref.blueprint, ref.version).getOrElse {
        Rules.require(ProcessRules.versionNotHeld(ref.blueprint, ref.version, None))
        throw IllegalStateException("unreachable")
      }
    )
    val worker = blueprint.steps.find(_.name == ref.step).flatMap(_.does.namedWorker)
    Rules.require(ProcessRules.actWorkerUnknown(ref.runId, ref.step, worker))
    Resolved(ref, run, version, worker.get, RunInput.parse(snapshot.input))

  /** Holds the run's version and the run, as ankka records them, when they are not held yet. */
  def hold(snapshot: RunSnapshot): BlueprintVersion =
    recorder
      .findVersion(snapshot.blueprint, snapshot.version)
      .getOrElse {
        val info = reads
          .versionInfo(snapshot.blueprint, snapshot.version)
          .getOrElse {
            Rules.require(ProcessRules.versionNotHeld(snapshot.blueprint, snapshot.version, None))
            throw IllegalStateException("unreachable")
          }
        recorder
          .holdVersion(
            BlueprintVersion(
              Ids.version(snapshot.blueprint, snapshot.version),
              snapshot.blueprint,
              snapshot.version,
              info.digest,
              Moment(info.registeredAt),
              recorder.now(),
              serviceWriter
            )
          )
          .record
      }

  /**
   * Holds the run as ankka records it, under the id it was asked for: the id a snapshot carries is
   * not relied on, since ankka's record of a run may answer without it.
   */
  def holdRun(snapshot: RunSnapshot, runId: String): Run =
    val _ = hold(snapshot)
    recorder
      .holdRun(
        Run(
          runId,
          snapshot.blueprint,
          snapshot.version,
          snapshot.startedBy,
          Moment(snapshot.startedAt),
          recorder.now(),
          serviceWriter
        )
      )
      .record
