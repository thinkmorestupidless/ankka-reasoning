package reasoning.belief.domain

/**
 * Why the service did not accept a write or could not answer a read.
 *
 * @param rule
 *   the rule's stable name, which a client or a test matches on
 * @param status
 *   the HTTP status it is answered with
 * @param error
 *   a sentence for a person; it may be reworded
 * @param names
 *   the identifiers and values that broke the rule
 */
final case class Refusal(
    rule: String,
    status: Int,
    error: String,
    names: Map[String, String] = Map.empty
)

/** Carries a refusal out of the code that found it. Nothing has been changed when it is thrown. */
final class Refused(val refusal: Refusal) extends RuntimeException(refusal.error)

object Refused:
  def apply(rule: String, status: Int, error: String, names: (String, String)*): Refused =
    new Refused(Refusal(rule, status, error, names.toMap))
