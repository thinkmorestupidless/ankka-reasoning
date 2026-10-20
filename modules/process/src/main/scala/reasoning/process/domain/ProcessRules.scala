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

  /** A run is started only of a blueprint the service carries. */
  def blueprintNotHeld(blueprint: String, carried: Set[String]): Option[Refusal] =
    if carried.contains(blueprint) then None
    else
      refusal(
        "blueprint.not-held",
        404,
        s"The service carries no blueprint '$blueprint'.",
        "blueprint" -> blueprint
      )

  def runIdHeldByAnother(run: String): Refusal =
    Refusal(
      "run.id.held-by-another",
      409,
      s"The run id '$run' is held by a run of a different blueprint or with a different input.",
      Map("run" -> run)
    )

  /**
   * The input of a run fits the blueprint's input shape; `names.field` is the first that does not.
   */
  def inputInvalid(field: String, problem: String): Refusal =
    Refusal(
      "run.input.invalid",
      422,
      s"The run's input does not fit the blueprint's input shape: $field $problem.",
      Map("field" -> field)
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

  /** The appraiser is registered again only with an instruction set the service carries. */
  def instructionsUnknown(instructions: String): Refusal =
    Refusal(
      "appraiser.instructions.unknown",
      422,
      s"The service carries no instruction set '$instructions'.",
      Map("instructions" -> instructions)
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

  /** A run's revision rests only on claims its own holder stated. */
  def restsOnOtherHolder(revision: String, claim: String, holder: String): Refusal =
    Refusal(
      "belief.rests-on.other-holder",
      422,
      "A belief revision a run writes rests only on claims stated by its holder.",
      Map("revision" -> revision, "claim" -> claim, "holder" -> holder)
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
