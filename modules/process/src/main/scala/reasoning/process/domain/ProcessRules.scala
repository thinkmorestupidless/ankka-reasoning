package reasoning.process.domain

import reasoning.belief.domain.{Moment, Refusal}

/**
 * Every rule of the process layer, as a function from facts already read to a refusal or nothing.
 * The names are those of the refusals contract. Nothing here reads anything.
 */
object ProcessRules:

  private def refusal(
      rule: String,
      status: Int,
      error: String,
      names: (String, String)*
  ): Option[Refusal] =
    Some(Refusal(rule, status, error, names.toMap))

  /** A run id whose records' ids fit, and that is not one ankka keeps for a schedule. */
  def runIdFormat(run: String): Option[Refusal] =
    if run.length <= Ids.RunIdLimit && !run.startsWith(Ids.SchedulePrefix) then None
    else
      refusal(
        "run.id.too-long",
        422,
        s"A run id is at most ${Ids.RunIdLimit} characters and does not start '${Ids.SchedulePrefix}'.",
        "run"   -> run,
        "limit" -> Ids.RunIdLimit.toString
      )

  def runNotHeld(run: String, held: Option[Run]): Option[Refusal] =
    if held.isDefined then None
    else refusal("run.not-held", 422, s"No run '$run' is held.", "run" -> run)

  def versionNotHeld(
      blueprint: String,
      version: Int,
      held: Option[BlueprintVersion]
  ): Option[Refusal] =
    if held.isDefined then None
    else
      refusal(
        "blueprint-version.not-held",
        422,
        s"No version $version of the blueprint '$blueprint' is held.",
        "blueprint" -> blueprint,
        "version"   -> version.toString
      )

  /** The step a tool is called in names one worker, or the act could not say who wrote. */
  def actWorkerUnknown(run: String, step: String, worker: Option[String]): Option[Refusal] =
    if worker.isDefined then None
    else
      refusal(
        "act.worker.unknown",
        422,
        s"The step '$step' of the run '$run' has no single worker, so a record it writes cannot name one.",
        "run"  -> run,
        "step" -> step
      )

  def actRecordNotHeld(record: RecordRef, held: Boolean): Option[Refusal] =
    if held then None
    else
      refusal(
        "act.record.not-held",
        422,
        s"No ${record.kind} '${record.id}' is held.",
        record.kind -> record.id
      )

  def documentNotInTranche(document: String, tranche: String, held: Boolean): Option[Refusal] =
    if held then None
    else
      refusal(
        "document.not-in-tranche",
        422,
        s"The tranche '$tranche' holds no document '$document'.",
        "document" -> document,
        "tranche"  -> tranche
      )

  def restFormat(rest: String): Refusal =
    Refusal(
      "belief.rest.format",
      422,
      "A rest is a claim's id, or a claim's id, '@' and a weight.",
      Map("rest" -> rest)
    )

  def toolCalledInNoRun(tool: String): Refusal =
    Refusal(
      "tool.called-in-no-run",
      422,
      s"The tool '$tool' writes only in a run of a blueprint.",
      Map("tool" -> tool)
    )

  def endingNotBeforeRun(run: Run, dated: Moment): Option[Refusal] =
    if dated >= run.dated then None
    else
      refusal(
        "run-ending.dated.before-run",
        422,
        "A run cannot end before it started.",
        "run"   -> run.id,
        "dated" -> dated.iso
      )
