package reasoning.belief.domain

/**
 * Every rule of the belief layer, as a function from facts already read to a refusal or nothing.
 *
 * The names are those of the refusals contract. Nothing here reads anything: the recorder reads the
 * records a write names and hands them over, and the entities apply the rules only they can.
 */
object Rules:

  val StatementLimit = 500
  val NameLimit      = 200
  val LocatorLimit   = 2000
  val ClaimLimit     = 1000
  val NoteLimit      = 500

  /** The first rule broken, thrown; nothing when none is. */
  def require(checks: Option[Refusal]*): Unit =
    checks
      .collectFirst { case Some(refusal) => refusal }
      .foreach(refusal => throw new Refused(refusal))

  private def refusal(
      rule: String,
      status: Int,
      error: String,
      names: (String, String)*
  ): Option[Refusal] =
    Some(Refusal(rule, status, error, names.toMap))

  // ── Any record ────────────────────────────────────────────────────────────

  def id(what: String, id: String, limit: Int = Ids.Limit): Option[Refusal] =
    if Ids.valid(id, limit) then None
    else
      refusal(
        "id.format",
        422,
        s"The identifier of a $what is 1 to $limit letters, digits, '.', '_' or '-', starting with a letter or digit.",
        what    -> id,
        "limit" -> limit.toString
      )

  def heldByAnother(what: String, id: String): Refusal =
    Refusal(
      "id.held-by-another",
      409,
      s"The identifier '$id' is held by a different $what.",
      Map(what -> id)
    )

  def notLaterThanNow(what: String, id: String, dated: Moment, now: Moment): Option[Refusal] =
    if dated <= now then None
    else
      refusal(
        "dated.later-than-now",
        422,
        s"A $what cannot be dated later than now.",
        what    -> id,
        "dated" -> dated.iso,
        "now"   -> now.iso
      )

  def text(what: String, id: String, field: String, value: String, limit: Int): Option[Refusal] =
    if value.nonEmpty && value.length <= limit then None
    else
      refusal(
        "text.length",
        422,
        s"The $field of a $what is 1 to $limit characters.",
        what    -> id,
        "field" -> field,
        "limit" -> limit.toString
      )

  /** A read named something that is not held. */
  def notHeld(what: String, id: String): Refused =
    Refused("record.not-held", 404, s"No $what '$id' is held.", what -> id)

  // ── Questions and holders ─────────────────────────────────────────────────

  def atLeastTwoHypotheses(question: String, count: Int): Option[Refusal] =
    if count >= 2 then None
    else
      refusal(
        "question.hypotheses.at-least-two",
        422,
        "A question is opened with at least two hypotheses.",
        "question" -> question
      )

  def questionNotHeld(question: String): Refusal =
    Refusal(
      "question.not-held",
      422,
      s"No question '$question' is held.",
      Map("question" -> question)
    )

  def hypothesisDatedBeforeQuestion(
      question: Question,
      hypothesis: String,
      dated: Moment
  ): Option[Refusal] =
    if dated >= question.dated then None
    else
      refusal(
        "question.hypothesis.dated-before-question",
        422,
        "A hypothesis cannot be dated before its question.",
        "question"   -> question.id,
        "hypothesis" -> hypothesis,
        "dated"      -> dated.iso
      )

  def holderKind(holder: String, kind: String, kinds: Set[String]): Option[Refusal] =
    if kinds.contains(kind) then None
    else
      refusal(
        "holder.kind.not-in-vocabulary",
        422,
        s"'$kind' is not a kind of holder the vocabulary names.",
        "holder" -> holder,
        "kind"   -> kind
      )

  def speaksFor(holder: Holder, writer: String): Option[Refusal] =
    if holder.spokenForBy(writer) then None
    else
      refusal(
        "holder.writer.does-not-speak",
        403,
        s"The writer does not speak for the holder '${holder.id}'.",
        "holder" -> holder.id,
        "writer" -> writer
      )

  // ── Evidence ──────────────────────────────────────────────────────────────

  def sourceNotHeld(source: String): Refusal =
    Refusal(
      "evidence.source.not-held",
      422,
      s"No source '$source' is registered.",
      Map("source" -> source)
    )

  def observedAfterPublished(
      evidence: String,
      observed: Moment,
      published: Option[Moment]
  ): Option[Refusal] =
    published.filter(_ > observed).flatMap { at =>
      refusal(
        "evidence.observed-before-published",
        422,
        "Evidence cannot be observed before it was published.",
        "evidence"    -> evidence,
        "observedAt"  -> observed.iso,
        "publishedAt" -> at.iso
      )
    }

  // ── Claims ────────────────────────────────────────────────────────────────

  def claimHolderNotHeld(claim: String, holder: String): Refusal =
    Refusal(
      "claim.holder.not-held",
      422,
      s"No holder '$holder' is held.",
      Map("claim" -> claim, "holder" -> holder)
    )

  def derivesFromSomething(claim: String, evidence: Vector[String]): Option[Refusal] =
    if evidence.nonEmpty then None
    else
      refusal(
        "claim.derives-from.none",
        422,
        "A claim derives from at least one piece of evidence.",
        "claim" -> claim
      )

  def claimEvidenceNotHeld(claim: String, evidence: String): Refusal =
    Refusal(
      "claim.derives-from.not-held",
      422,
      s"No evidence '$evidence' is held.",
      Map("claim" -> claim, "evidence" -> evidence)
    )

  def takesAStance(claim: String, stances: Vector[Stance]): Option[Refusal] =
    if stances.nonEmpty then None
    else
      refusal(
        "claim.stance.none",
        422,
        "A claim takes a stance on at least one hypothesis.",
        "claim" -> claim
      )

  def stanceKnown(claim: String, stance: Stance): Option[Refusal] =
    if Stance.all.contains(stance.stance) then None
    else
      refusal(
        "claim.stance.unknown",
        422,
        s"A stance is '${Stance.Supports}' or '${Stance.Contradicts}'.",
        "claim"  -> claim,
        "stance" -> stance.stance
      )

  def oneStancePerHypothesis(claim: String, stances: Vector[Stance]): Option[Refusal] =
    stances
      .groupBy(_.hypothesis)
      .collectFirst { case (hypothesis, all) if all.sizeIs > 1 => hypothesis }
      .flatMap { hypothesis =>
        refusal(
          "claim.stance.twice-on-one-hypothesis",
          422,
          "A claim takes at most one stance on a hypothesis.",
          "claim"      -> claim,
          "hypothesis" -> hypothesis
        )
      }

  def claimHypothesisNotHeld(claim: String, hypothesis: String): Refusal =
    Refusal(
      "claim.stance.hypothesis-not-held",
      422,
      s"No hypothesis '$hypothesis' is held.",
      Map("claim" -> claim, "hypothesis" -> hypothesis)
    )

  def revisedClaimNotHeld(claim: String, revised: String): Refusal =
    Refusal(
      "claim.revises.not-held",
      422,
      s"No claim '$revised' is held.",
      Map("claim" -> claim, "revises" -> revised)
    )

  def revisesAnother(claim: String, revises: Option[String]): Option[Refusal] =
    if !revises.contains(claim) then None
    else refusal("claim.revises.itself", 422, "A claim cannot revise itself.", "claim" -> claim)

  def claimNotBeforeHypothesis(
      claim: String,
      dated: Moment,
      reference: String,
      hypothesis: Hypothesis
  ): Option[Refusal] =
    if dated >= hypothesis.dated then None
    else
      refusal(
        "claim.dated.not-before-hypothesis",
        422,
        "A claim cannot be dated before a hypothesis it takes a stance on.",
        "claim"      -> claim,
        "hypothesis" -> reference,
        "dated"      -> dated.iso
      )

  def claimNotBeforeEvidence(claim: String, dated: Moment, evidence: Evidence): Option[Refusal] =
    if dated >= evidence.dated then None
    else
      refusal(
        "claim.dated.not-before-evidence",
        422,
        "A claim cannot be dated before the evidence it derives from.",
        "claim"    -> claim,
        "evidence" -> evidence.id,
        "dated"    -> dated.iso
      )

  def claimNotBeforeRevised(claim: String, dated: Moment, revised: Claim): Option[Refusal] =
    if dated >= revised.dated then None
    else
      refusal(
        "claim.dated.not-before-revised",
        422,
        "A claim cannot be dated before the claim it revises.",
        "claim"   -> claim,
        "revises" -> revised.id,
        "dated"   -> dated.iso
      )

  // ── Withdrawal ────────────────────────────────────────────────────────────

  def withdrawalNote(what: String, id: String, note: Option[String]): Option[Refusal] =
    note.map(_.trim).filter(_.nonEmpty) match
      case None =>
        refusal("withdrawal.note.none", 422, "A withdrawal gives a note saying why.", what -> id)
      case Some(text) => Rules.text(what, id, "note", text, NoteLimit)

  def mayWithdraw(what: String, id: String, writer: String, allowed: Boolean): Option[Refusal] =
    if allowed then None
    else
      refusal(
        "withdrawal.writer.may-not",
        403,
        s"The writer may not withdraw the text of this $what.",
        what     -> id,
        "writer" -> writer
      )

  // ── Beliefs ───────────────────────────────────────────────────────────────

  def beliefHolderNotHeld(revision: String, holder: String): Refusal =
    Refusal(
      "belief.holder.not-held",
      422,
      s"No holder '$holder' is held.",
      Map("revision" -> revision, "holder" -> holder)
    )

  def beliefHypothesisNotHeld(revision: String, hypothesis: String): Refusal =
    Refusal(
      "belief.hypothesis.not-held",
      422,
      s"No hypothesis '$hypothesis' is held.",
      Map("revision" -> revision, "hypothesis" -> hypothesis)
    )

  def probability(revision: String, value: Double): Option[Refusal] =
    if value >= 0.0 && value <= 1.0 then None
    else
      refusal(
        "belief.probability.range",
        422,
        "A probability is from nought to one.",
        "revision"    -> revision,
        "probability" -> value.toString
      )

  def weight(revision: String, rest: Rest): Option[Refusal] =
    rest.weight.filterNot(w => w >= 0.0 && w <= 1.0).flatMap { w =>
      refusal(
        "belief.weight.range",
        422,
        "A weight is from nought to one.",
        "revision" -> revision,
        "claim"    -> rest.claim,
        "weight"   -> w.toString
      )
    }

  def eachClaimOnce(revision: String, restsOn: Vector[Rest]): Option[Refusal] =
    restsOn.groupBy(_.claim).collectFirst { case (claim, all) if all.sizeIs > 1 => claim }.flatMap {
      claim =>
        refusal(
          "belief.rests-on.claim-twice",
          422,
          "A belief revision rests on a claim at most once.",
          "revision" -> revision,
          "claim"    -> claim
        )
    }

  def restedClaimNotHeld(revision: String, claim: String): Refusal =
    Refusal(
      "belief.rests-on.not-held",
      422,
      s"No claim '$claim' is held.",
      Map("revision" -> revision, "claim" -> claim)
    )

  def claimAboutQuestion(revision: String, question: String, claim: Claim): Option[Refusal] =
    val about =
      claim.stances.flatMap(s => HypothesisRef.parse(s.hypothesis)).exists(_.question == question)
    if about then None
    else
      refusal(
        "belief.rests-on.other-question",
        422,
        "A belief revision rests only on claims that take a stance on a hypothesis of its own question.",
        "revision" -> revision,
        "claim"    -> claim.id,
        "question" -> question
      )

  def revisionNotBeforeHypothesis(
      revision: String,
      dated: Moment,
      reference: String,
      hypothesis: Hypothesis
  ): Option[Refusal] =
    if dated >= hypothesis.dated then None
    else
      refusal(
        "belief.dated.not-before-hypothesis",
        422,
        "A belief revision cannot be dated before its hypothesis.",
        "revision"   -> revision,
        "hypothesis" -> reference,
        "dated"      -> dated.iso
      )

  def revisionNotBeforeClaim(revision: String, dated: Moment, claim: Claim): Option[Refusal] =
    if dated >= claim.dated then None
    else
      refusal(
        "belief.dated.not-before-claim",
        422,
        "A belief revision cannot rest on a claim dated later than itself.",
        "revision" -> revision,
        "claim"    -> claim.id,
        "dated"    -> dated.iso
      )

  def revisionNotBeforeCurrent(
      revision: String,
      dated: Moment,
      current: Revision
  ): Option[Refusal] =
    if dated >= current.dated then None
    else
      refusal(
        "belief.dated.not-before-current",
        422,
        "A belief revision cannot be dated before the one it follows.",
        "revision" -> revision,
        "current"  -> current.id,
        "dated"    -> dated.iso
      )

  def followsCurrent(
      revision: String,
      follows: Option[String],
      current: Option[Revision]
  ): Option[Refusal] =
    if follows == current.map(_.id) then None
    else
      refusal(
        "belief.follows.not-current",
        409,
        current.fold("The belief has no revision yet, so a revision of it follows none.")(c =>
          s"A revision follows the belief's current revision, which is '${c.id}'."
        ),
        (Seq("revision" -> revision) ++ current.map(c => "current" -> c.id) ++ follows.map(
          "follows" -> _
        ))*
      )
