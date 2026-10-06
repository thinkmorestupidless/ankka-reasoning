package reasoning.belief.application

import com.thinkmorestupidless.ankka.core.EntityId
import com.thinkmorestupidless.ankka.sdk.ComponentClient
import reasoning.belief.application.graph.GraphOf
import reasoning.belief.domain.*
import reasoning.graph.Element

/**
 * What the service hands the belief layer: the kinds of holder every registered layer names, the
 * longest excerpt, and the writers who may withdraw the text of any record. The layer reads no
 * settings itself.
 */
final case class BeliefConfig(holderKinds: Set[String], excerptLimit: Int, stewards: Set[String])

// ── What a writer sends ─────────────────────────────────────────────────────

final case class HypothesisRequest(id: String, statement: String)

final case class OpenQuestion(
    id: String,
    statement: String,
    hypotheses: Vector[HypothesisRequest],
    dated: Option[Moment] = None
)

final case class AddHypothesis(id: String, statement: String, dated: Option[Moment] = None)

final case class RegisterHolder(id: String, kind: String, name: String)

final case class AddWriterRequest(writer: String)

final case class RegisterSource(id: String, name: String)

final case class RecordEvidence(
    source: String,
    locator: String,
    excerpt: String,
    author: Option[String] = None,
    publishedAt: Option[Moment] = None,
    observedAt: Option[Moment] = None
)

final case class StateClaim(
    id: String,
    holder: String,
    statement: String,
    derivesFrom: Vector[String],
    stances: Vector[Stance],
    revises: Option[String] = None,
    dated: Option[Moment] = None
)

final case class StateBelief(
    id: String,
    holder: String,
    hypothesis: String,
    probability: Double,
    restsOn: Vector[Rest] = Vector.empty,
    follows: Option[String] = None,
    dated: Option[Moment] = None
)

/** Why a record's text is to be withdrawn. Without a note the withdrawal is refused by name. */
final case class Withdraw(note: Option[String] = None)

/** A belief as its entity knows it: how many revisions it has had and the current one. */
final case class BeliefHead(
    holder: String,
    hypothesis: String,
    count: Int,
    current: Option[Revision]
)

/** Part of a belief's line, newest first, and whether older revisions remain. */
final case class LinePage(revisions: Vector[Revision], more: Boolean)

/**
 * Takes a record from a writer, checks it against the records it names, and sends it to its own
 * entity.
 *
 * The check comes before the command and is still true after it, because nothing a rule here
 * depends on can stop being true: a record that is held stays held, its date and links never
 * change, and a question's hypotheses and a holder's writers are only added to. The two rules that
 * do depend on changing state, which revision is current and whether a price has moved, are decided
 * by the belief's entity.
 *
 * Every read is of an entity by its id, never of a view or of the graph.
 */
final class Recorder(client: ComponentClient, clock: Clock, config: BeliefConfig):

  // ── Reads ─────────────────────────────────────────────────────────────────

  def findQuestion(id: String): Option[Question] =
    if !Ids.valid(id) then None
    else client.forKeyValueEntity(EntityId(id)).call(QuestionEntity.get).invoke().held

  def findHolder(id: String): Option[Holder] =
    if !Ids.valid(id) then None
    else client.forKeyValueEntity(EntityId(id)).call(HolderEntity.get).invoke().held

  def findSource(id: String): Option[Source] =
    if !Ids.valid(id) then None
    else client.forKeyValueEntity(EntityId(id)).call(SourceEntity.get).invoke().held

  def findEvidence(id: String): Option[Evidence] =
    if !Ids.valid(id) then None
    else client.forKeyValueEntity(EntityId(id)).call(EvidenceEntity.get).invoke().held

  def findClaim(id: String): Option[Claim] =
    if !Ids.valid(id) then None
    else client.forKeyValueEntity(EntityId(id)).call(ClaimEntity.get).invoke().held

  def findRevision(id: String): Option[Revision] =
    if !Ids.valid(id) then None
    else client.forKeyValueEntity(EntityId(id)).call(RevisionRecordEntity.get).invoke().held

  /** A hypothesis, when its question is held and has it. */
  def findHypothesis(reference: String): Option[(Question, Hypothesis)] =
    for
      ref        <- HypothesisRef.parse(reference)
      question   <- findQuestion(ref.question)
      hypothesis <- question.hypothesis(ref.hypothesis)
    yield (question, hypothesis)

  def question(id: String): Question =
    findQuestion(id).getOrElse(throw Rules.notHeld("question", id))
  def holder(id: String): Holder = findHolder(id).getOrElse(throw Rules.notHeld("holder", id))
  def source(id: String): Source = findSource(id).getOrElse(throw Rules.notHeld("source", id))
  def evidence(id: String): Evidence =
    findEvidence(id).getOrElse(throw Rules.notHeld("evidence", id))
  def claim(id: String): Claim = findClaim(id).getOrElse(throw Rules.notHeld("claim", id))
  def revision(id: String): Revision =
    findRevision(id).getOrElse(throw Rules.notHeld("revision", id))

  /** One holder's belief in one hypothesis, as its entity has it now. */
  def belief(holder: String, hypothesis: String): BeliefHead =
    val ref =
      HypothesisRef.parse(hypothesis).getOrElse(throw Rules.notHeld("hypothesis", hypothesis))
    if !Ids.valid(holder) then throw Rules.notHeld("holder", holder)
    val state = client
      .forEventSourcedEntity(EntityId(Ids.belief(holder, ref)))
      .call(BeliefEntity.get)
      .invoke()
    BeliefHead(holder, ref.render, state.count, state.current)

  /**
   * A belief's revisions, newest first, from its head or from the revision before `before`, walked
   * back through `follows` a page at a time.
   */
  def line(holder: String, hypothesis: String, before: Option[String], limit: Int): LinePage =
    val head = belief(holder, hypothesis)
    val start: Option[Revision] = before match
      case None => head.current
      case Some(id) =>
        val from = revision(id)
        if from.holder != head.holder || from.hypothesis != head.hypothesis then
          throw Rules.notHeld("revision", id)
        from.follows.flatMap(findRevision)
    val page = Iterator
      .iterate(start)(_.flatMap(_.follows).flatMap(findRevision))
      .takeWhile(_.isDefined)
      .flatten
      .take(limit.max(1).min(100))
      .toVector
    LinePage(page, page.lastOption.exists(_.follows.isDefined))

  // ── What a record is in the graph ─────────────────────────────────────────

  /**
   * A record's elements and the version they are published at, for whoever waits for the graph to
   * hold it. `None` when the kind is not one of this layer's or the record is not held.
   */
  def published(kind: String, id: String): Option[(Vector[Element], Long)] =
    def versionOf(read: => Long): Long = read
    kind match
      case "question" =>
        findQuestion(id).map(r =>
          GraphOf.question(r) -> versionOf(
            client.forKeyValueEntity(EntityId(id)).call(QuestionEntity.version).invoke()
          )
        )
      case "holder" =>
        findHolder(id).map(r =>
          GraphOf.holder(r) -> versionOf(
            client.forKeyValueEntity(EntityId(id)).call(HolderEntity.version).invoke()
          )
        )
      case "source" =>
        findSource(id).map(r =>
          GraphOf.source(r) -> versionOf(
            client.forKeyValueEntity(EntityId(id)).call(SourceEntity.version).invoke()
          )
        )
      case "evidence" =>
        findEvidence(id).map(r =>
          GraphOf.evidence(r) -> versionOf(
            client.forKeyValueEntity(EntityId(id)).call(EvidenceEntity.version).invoke()
          )
        )
      case "claim" =>
        findClaim(id).map(r =>
          GraphOf.claim(r) -> versionOf(
            client.forKeyValueEntity(EntityId(id)).call(ClaimEntity.version).invoke()
          )
        )
      case "revision" => findRevision(id).map(r => GraphOf.revision(r) -> r.sequence)
      case _          => None

  // ── Writes ────────────────────────────────────────────────────────────────

  def openQuestion(request: OpenQuestion, writer: String): Recorded[Question] =
    val now   = clock.now()
    val dated = request.dated.getOrElse(now)
    val twice =
      request.hypotheses.groupBy(_.id).collectFirst { case (id, all) if all.sizeIs > 1 => id }
    Rules.require(
      (Seq(
        Rules.id("question", request.id),
        Rules.text("question", request.id, "statement", request.statement, Rules.StatementLimit),
        Rules.atLeastTwoHypotheses(request.id, request.hypotheses.size)
      ) ++ request.hypotheses.flatMap(h =>
        Seq(
          Rules.id("hypothesis", h.id),
          Rules.text(
            "hypothesis",
            s"${request.id}/${h.id}",
            "statement",
            h.statement,
            Rules.StatementLimit
          )
        )
      ) ++ Seq(
        twice.map(id => Rules.heldByAnother("hypothesis", s"${request.id}/$id")),
        Rules.notLaterThanNow("question", request.id, dated, now)
      ))*
    )
    val record = Question(
      request.id,
      request.statement,
      request.hypotheses.map(h => Hypothesis(h.id, h.statement, dated, now, writer)),
      dated,
      now,
      writer
    )
    client
      .forKeyValueEntity(EntityId(request.id))
      .call(QuestionEntity.open)
      .invoke(Put(record, request.dated.isDefined))
      .recorded

  def addHypothesis(question: String, request: AddHypothesis, writer: String): Recorded[Question] =
    val now   = clock.now()
    val dated = request.dated.getOrElse(now)
    if !Ids.valid(question) then throw new Refused(Rules.questionNotHeld(question))
    Rules.require(
      Rules.id("hypothesis", request.id),
      Rules.text(
        "hypothesis",
        s"$question/${request.id}",
        "statement",
        request.statement,
        Rules.StatementLimit
      ),
      Rules.notLaterThanNow("hypothesis", s"$question/${request.id}", dated, now)
    )
    client
      .forKeyValueEntity(EntityId(question))
      .call(QuestionEntity.addHypothesis)
      .invoke(
        Put(Hypothesis(request.id, request.statement, dated, now, writer), request.dated.isDefined)
      )
      .recorded

  def registerHolder(request: RegisterHolder, writer: String): Recorded[Holder] =
    val now = clock.now()
    Rules.require(
      Rules.id("holder", request.id),
      Rules.holderKind(request.id, request.kind, config.holderKinds),
      Rules.text("holder", request.id, "name", request.name, Rules.NameLimit)
    )
    client
      .forKeyValueEntity(EntityId(request.id))
      .call(HolderEntity.register)
      .invoke(Holder(request.id, request.kind, request.name, Vector(writer), now, now, writer))
      .recorded

  def addWriter(holder: String, request: AddWriterRequest, writer: String): Recorded[Holder] =
    if !Ids.valid(holder) then throw Rules.notHeld("holder", holder)
    Rules.require(Rules.text("holder", holder, "writer", request.writer, Rules.NameLimit))
    client
      .forKeyValueEntity(EntityId(holder))
      .call(HolderEntity.addWriter)
      .invoke(AddWriter(request.writer, writer))
      .recorded

  def registerSource(request: RegisterSource, writer: String): Recorded[Source] =
    val now = clock.now()
    Rules.require(
      Rules.id("source", request.id),
      Rules.text("source", request.id, "name", request.name, Rules.NameLimit)
    )
    client
      .forKeyValueEntity(EntityId(request.id))
      .call(SourceEntity.register)
      .invoke(Source(request.id, request.name, now, now, writer))
      .recorded

  def recordEvidence(request: RecordEvidence, writer: String): Recorded[Evidence] =
    val now      = clock.now()
    val observed = request.observedAt.getOrElse(now)
    val id       = Evidence.digest(request.source, request.locator, request.excerpt)
    Rules.require(
      Rules.id("source", request.source).map(_ => Rules.sourceNotHeld(request.source)),
      Rules.text("evidence", id, "locator", request.locator, Rules.LocatorLimit),
      Rules.text("evidence", id, "excerpt", request.excerpt, config.excerptLimit),
      request.author.flatMap(a => Rules.text("evidence", id, "author", a, Rules.NameLimit)),
      Rules.notLaterThanNow("evidence", id, observed, now),
      Rules.observedAfterPublished(id, observed, request.publishedAt)
    )
    if findSource(request.source).isEmpty then
      throw new Refused(Rules.sourceNotHeld(request.source))
    client
      .forKeyValueEntity(EntityId(id))
      .call(EvidenceEntity.record)
      .invoke(
        Evidence(
          id,
          request.source,
          Some(request.locator),
          Some(request.excerpt),
          request.author,
          request.publishedAt,
          observed,
          now,
          writer
        )
      )
      .recorded

  def stateClaim(request: StateClaim, writer: String): Recorded[Claim] =
    val now   = clock.now()
    val dated = request.dated.getOrElse(now)
    val id    = request.id
    Rules.require(
      (Seq(
        Rules.id("claim", id),
        Rules.text("claim", id, "statement", request.statement, Rules.ClaimLimit),
        Rules.derivesFromSomething(id, request.derivesFrom),
        Rules.takesAStance(id, request.stances)
      ) ++ request.stances.map(Rules.stanceKnown(id, _)) ++ Seq(
        Rules.oneStancePerHypothesis(id, request.stances),
        Rules.revisesAnother(id, request.revises),
        Rules.notLaterThanNow("claim", id, dated, now)
      ))*
    )
    val holder = findHolder(request.holder).getOrElse(
      throw new Refused(Rules.claimHolderNotHeld(id, request.holder))
    )
    Rules.require(Rules.speaksFor(holder, writer))
    request.stances.foreach { stance =>
      val (_, hypothesis) = findHypothesis(stance.hypothesis)
        .getOrElse(throw new Refused(Rules.claimHypothesisNotHeld(id, stance.hypothesis)))
      Rules.require(Rules.claimNotBeforeHypothesis(id, dated, stance.hypothesis, hypothesis))
    }
    request.derivesFrom.foreach { evidenceId =>
      val evidence = findEvidence(evidenceId).getOrElse(
        throw new Refused(Rules.claimEvidenceNotHeld(id, evidenceId))
      )
      Rules.require(Rules.claimNotBeforeEvidence(id, dated, evidence))
    }
    request.revises.foreach { revisedId =>
      val revised =
        findClaim(revisedId).getOrElse(throw new Refused(Rules.revisedClaimNotHeld(id, revisedId)))
      Rules.require(Rules.claimNotBeforeRevised(id, dated, revised))
    }
    val record = Claim(
      id,
      request.holder,
      Some(request.statement),
      request.derivesFrom,
      request.stances,
      request.revises,
      dated,
      now,
      writer
    )
    client
      .forKeyValueEntity(EntityId(id))
      .call(ClaimEntity.state)
      .invoke(Put(record, request.dated.isDefined))
      .recorded

  /**
   * Withdraws the text of a piece of evidence: its locator, its excerpt and its author. The writer
   * who recorded it may, and a steward.
   */
  def withdrawEvidence(id: String, request: Withdraw, writer: String): Recorded[Evidence] =
    val held = evidence(id)
    Rules.require(
      Rules.withdrawalNote("evidence", id, request.note),
      Rules.mayWithdraw(
        "evidence",
        id,
        writer,
        held.writer == writer || config.stewards.contains(writer)
      )
    )
    client
      .forKeyValueEntity(EntityId(id))
      .call(EvidenceEntity.withdraw)
      .invoke(Withdrawal(request.note.get.trim, writer, clock.now()))
      .recorded

  /** Withdraws a claim's statement. A writer who speaks for its holder may, and a steward. */
  def withdrawClaim(id: String, request: Withdraw, writer: String): Recorded[Claim] =
    val held   = claim(id)
    val speaks = findHolder(held.holder).exists(_.spokenForBy(writer))
    Rules.require(
      Rules.withdrawalNote("claim", id, request.note),
      Rules.mayWithdraw("claim", id, writer, speaks || config.stewards.contains(writer))
    )
    client
      .forKeyValueEntity(EntityId(id))
      .call(ClaimEntity.withdraw)
      .invoke(Withdrawal(request.note.get.trim, writer, clock.now()))
      .recorded

  /**
   * States a revision of a belief.
   *
   * @param unlessUnchanged
   *   answer with the current revision when this one has the same probability and both rest on no
   *   claims
   */
  def stateBelief(
      request: StateBelief,
      writer: String,
      unlessUnchanged: Boolean = false
  ): Recorded[Revision] =
    val now   = clock.now()
    val dated = request.dated.getOrElse(now)
    val id    = request.id
    Rules.require(
      (Seq(
        Rules.id("revision", id),
        Rules.probability(id, request.probability)
      ) ++ request.restsOn.map(Rules.weight(id, _)) ++ Seq(
        Rules.eachClaimOnce(id, request.restsOn),
        Rules.notLaterThanNow("revision", id, dated, now)
      ))*
    )
    val holder = findHolder(request.holder).getOrElse(
      throw new Refused(Rules.beliefHolderNotHeld(id, request.holder))
    )
    Rules.require(Rules.speaksFor(holder, writer))
    val ref = HypothesisRef
      .parse(request.hypothesis)
      .getOrElse(throw new Refused(Rules.beliefHypothesisNotHeld(id, request.hypothesis)))
    val (_, hypothesis) = findHypothesis(request.hypothesis)
      .getOrElse(throw new Refused(Rules.beliefHypothesisNotHeld(id, request.hypothesis)))
    Rules.require(Rules.revisionNotBeforeHypothesis(id, dated, ref.render, hypothesis))
    request.restsOn.foreach { rest =>
      val claim =
        findClaim(rest.claim).getOrElse(throw new Refused(Rules.restedClaimNotHeld(id, rest.claim)))
      Rules.require(
        Rules.claimAboutQuestion(id, ref.question, claim),
        Rules.revisionNotBeforeClaim(id, dated, claim)
      )
    }
    val sent = Revision(
      id,
      request.holder,
      ref.render,
      n = 0,
      request.probability,
      request.restsOn,
      request.follows,
      dated,
      now,
      writer
    )
    val stated = request.dated.isDefined
    findRevision(id) match
      case Some(held) if Same.revision(held, sent, stated) => Recorded(held, created = false)
      case Some(_) => throw new Refused(Rules.heldByAnother("revision", id))
      case None =>
        val recorded = client
          .forEventSourcedEntity(EntityId(Ids.belief(request.holder, ref)))
          .call(BeliefEntity.stateRevision)
          .invoke(StateRevision(sent, stated, unlessUnchanged))
          .recorded
        // So the writer can read it back at once. The consumer over the belief's events writes it
        // too, which is what guarantees it.
        client
          .forKeyValueEntity(EntityId(recorded.record.id))
          .call(RevisionRecordEntity.put)
          .invoke(recorded.record): Unit
        recorded
