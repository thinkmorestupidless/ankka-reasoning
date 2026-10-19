package reasoning.process.answers

import reasoning.belief.domain.Moment
import reasoning.process.domain.RecordRef

/**
 * What an answer with acts is made of: the process layer's records as the graph holds them. Every
 * field is a property of a node or an identifier; no answer carries a sentence the service wrote.
 */

final case class VersionView(
    id: String,
    blueprint: String,
    version: Int,
    digest: String,
    dated: Moment,
    recordedAt: Moment
)

final case class RunView(
    id: String,
    blueprint: String,
    version: Int,
    startedBy: String,
    dated: Moment,
    recordedAt: Moment
)

final case class RunEndingView(
    run: String,
    status: String,
    reason: Option[String],
    dated: Moment,
    recordedAt: Moment
)

final case class ActView(
    id: String,
    step: String,
    worker: String,
    recordKind: String,
    dated: Moment,
    recordedAt: Moment
)

/**
 * A record's provenance: the act that wrote it, in which run, how that run ended, of which version.
 */
final case class ActOf(
    record: RecordRef,
    act: ActView,
    run: RunView,
    runEnding: Option[RunEndingView],
    blueprintVersion: VersionView
)

/** A lower layer's answer, unchanged, with the act of every record in it that has one. */
final case class WithActs[A](answer: A, acts: Vector[ActOf])

/** One thing a run wrote: the act and the record it names. */
final case class Wrote(act: ActView, record: RecordRef)

/** Everything a run wrote, from the graph: the run, its ending once it has one, and its acts. */
final case class RunWrote(run: RunView, ending: Option[RunEndingView], wrote: Vector[Wrote])
