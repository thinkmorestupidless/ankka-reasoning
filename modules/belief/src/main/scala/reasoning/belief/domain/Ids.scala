package reasoning.belief.domain

/**
 * Identifiers, which are the writer's to choose.
 *
 * At most 64 characters, because an entity's id and its component's name share a 255-character
 * column in ankka's schema and a belief's id is three identifiers joined.
 */
object Ids:

  val Limit: Int = 64

  private val Format = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def valid(id: String, limit: Int = Limit): Boolean =
    id.nonEmpty && id.length <= limit && Format.matches(id)

  /** The id of the belief one holder has in one hypothesis: its entity's id. */
  def belief(holder: String, hypothesis: HypothesisRef): String =
    s"$holder~${hypothesis.question}~${hypothesis.hypothesis}"

/**
 * A hypothesis, named by its question and its own identifier within it: `<question>/<hypothesis>`.
 */
final case class HypothesisRef(question: String, hypothesis: String):
  def render: String            = s"$question/$hypothesis"
  override def toString: String = render

object HypothesisRef:
  def parse(text: String): Option[HypothesisRef] = text.split('/') match
    case Array(question, hypothesis) if Ids.valid(question) && Ids.valid(hypothesis) =>
      Some(HypothesisRef(question, hypothesis))
    case _ => None
