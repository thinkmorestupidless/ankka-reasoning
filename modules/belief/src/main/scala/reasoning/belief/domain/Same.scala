package reasoning.belief.domain

/**
 * Whether a record sent again is the record already held.
 *
 * It compares what the writer supplied and nothing the service set: not the time recorded, not the
 * writer, and the date only when the writer stated one. A record whose text has been withdrawn is
 * the same record as one sent with its text.
 */
object Same:

  private def date(held: Moment, sent: Moment, stated: Boolean): Boolean = !stated || held == sent

  def question(held: Question, sent: Question, datedStated: Boolean): Boolean =
    held.statement == sent.statement &&
      held.hypotheses.take(sent.hypotheses.size).map(h => (h.id, h.statement)) ==
      sent.hypotheses.map(h => (h.id, h.statement)) &&
      date(held.dated, sent.dated, datedStated)

  def hypothesis(held: Hypothesis, sent: Hypothesis, datedStated: Boolean): Boolean =
    held.statement == sent.statement && date(held.dated, sent.dated, datedStated)

  def holder(held: Holder, sent: Holder): Boolean = held.kind == sent.kind && held.name == sent.name

  def source(held: Source, sent: Source): Boolean = held.name == sent.name

  def claim(held: Claim, sent: Claim, datedStated: Boolean): Boolean =
    held.holder == sent.holder &&
      (held.withdrawn || held.statement == sent.statement) &&
      held.derivesFrom.toSet == sent.derivesFrom.toSet &&
      held.stances.toSet == sent.stances.toSet &&
      held.revises == sent.revises &&
      date(held.dated, sent.dated, datedStated)

  def revision(held: Revision, sent: Revision, datedStated: Boolean): Boolean =
    held.holder == sent.holder &&
      held.hypothesis == sent.hypothesis &&
      held.probability == sent.probability &&
      held.restsOn.toSet == sent.restsOn.toSet &&
      held.follows == sent.follows &&
      date(held.dated, sent.dated, datedStated)
