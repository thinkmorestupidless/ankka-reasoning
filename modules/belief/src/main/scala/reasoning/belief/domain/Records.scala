package reasoning.belief.domain

/**
 * The records of the belief layer, as they are held and as they are read back.
 *
 * Every record has the identifier it is known by, the date it holds from, the time the service
 * accepted it and the writer who sent it. None is changed once held, except that a hypothesis may
 * be added to a question, a writer to a holder, and the text of evidence or of a claim withdrawn.
 */

/** One of the answers that compete for a question. Its id is unique within its question. */
final case class Hypothesis(
    id: String,
    statement: String,
    dated: Moment,
    recordedAt: Moment,
    writer: String
)

/** Something not yet known, with the hypotheses that compete to answer it. */
final case class Question(
    id: String,
    statement: String,
    hypotheses: Vector[Hypothesis],
    dated: Moment,
    recordedAt: Moment,
    writer: String
):
  def hypothesis(id: String): Option[Hypothesis] = hypotheses.find(_.id == id)

/** Whoever holds a belief or states a claim, and the writers who may do so in its name. */
final case class Holder(
    id: String,
    kind: String,
    name: String,
    writers: Vector[String],
    dated: Moment,
    recordedAt: Moment,
    writer: String
):
  def spokenForBy(writer: String): Boolean = writers.contains(writer)

/** Where evidence comes from. */
final case class Source(id: String, name: String, dated: Moment, recordedAt: Moment, writer: String)

/** That a record's text was taken out for good: why, by whom and when. */
final case class Withdrawal(note: String, writer: String, at: Moment)

/**
 * Something observed, with its provenance. Its id is the digest of its source, locator and excerpt,
 * so the same evidence recorded twice is one. Its locator, excerpt and author are its text, absent
 * once withdrawn. It is dated the time it was observed.
 */
final case class Evidence(
    id: String,
    source: String,
    locator: Option[String],
    excerpt: Option[String],
    author: Option[String],
    publishedAt: Option[Moment],
    dated: Moment,
    recordedAt: Moment,
    writer: String,
    withdrawal: Option[Withdrawal] = None
):
  def withdrawn: Boolean = withdrawal.isDefined

object Evidence:

  /** The identifier of the evidence a source, a locator and an excerpt make. */
  def digest(source: String, locator: String, excerpt: String): String =
    val bytes = s"$source\n$locator\n$excerpt\n".getBytes("UTF-8")
    java.util.HexFormat
      .of()
      .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))

/** How a claim bears on one hypothesis: `supports` or `contradicts`. */
final case class Stance(hypothesis: String, stance: String)

object Stance:
  val Supports    = "supports"
  val Contradicts = "contradicts"
  val all         = Set(Supports, Contradicts)

/** A statement a holder derives from evidence, with a stance on one or more hypotheses. */
final case class Claim(
    id: String,
    holder: String,
    statement: Option[String],
    derivesFrom: Vector[String],
    stances: Vector[Stance],
    revises: Option[String],
    dated: Moment,
    recordedAt: Moment,
    writer: String,
    withdrawal: Option[Withdrawal] = None
):
  def withdrawn: Boolean = withdrawal.isDefined

/** A claim a belief revision rests on, with the weight the holder gives it. */
final case class Rest(claim: String, weight: Option[Double] = None)

/**
 * One statement of a belief: the probability at that time and the claims it rests on.
 *
 * @param n
 *   its place in its belief's line, from 1
 * @param follows
 *   the revision before it; absent for the first
 * @param sequence
 *   the sequence number of its event, which is its version in the graph
 */
final case class Revision(
    id: String,
    holder: String,
    hypothesis: String,
    n: Int,
    probability: Double,
    restsOn: Vector[Rest],
    follows: Option[String],
    dated: Moment,
    recordedAt: Moment,
    writer: String,
    sequence: Long = 0L
)

/** What a write is answered with: the record as held, and whether this write created it. */
final case class Recorded[A](record: A, created: Boolean)

/**
 * What an entity answers a write with: the record and whether it is new, or the refusal. A refusal
 * is a reply and not an error because it carries the rule's name and what broke it.
 */
final case class Outcome[A](record: Option[A], created: Boolean, refusal: Option[Refusal]):
  /** The record, or the refusal thrown. */
  def recorded: Recorded[A] = (record, refusal) match
    case (Some(held), _)   => Recorded(held, created)
    case (None, Some(why)) => throw new Refused(why)
    case (None, None) =>
      throw IllegalStateException("an outcome with neither a record nor a refusal")

object Outcome:
  def created[A](record: A): Outcome[A]        = Outcome(Some(record), created = true, None)
  def repeat[A](record: A): Outcome[A]         = Outcome(Some(record), created = false, None)
  def refused[A](refusal: Refusal): Outcome[A] = Outcome(None, created = false, Some(refusal))
