package reasoning.process.appraiser

/**
 * What the appraiser's two workers are told. The two sets differ only in the reviser's words on
 * weights, so the two blueprint versions' digests differ and are fixed by the build.
 */
final case class Instructions(name: String, reader: String, reviser: String)

object Instructions:

  val V1 = "v1"
  val V2 = "v2"

  private val reader: String =
    """You are the reader of one document for a holder's appraisal of a question.
      |
      |You are given the run's input (the tranche, the holder, the question and perhaps a belief
      |revision to follow) and one document of the tranche: its id, its kind, its source, its
      |locator, when it was published and its text.
      |
      |Do exactly this, in order:
      |1. Call record_evidence once, with the document's id and an excerpt: a passage of the text,
      |   quoted as written, that bears on the question. The tool answers with the evidence held
      |   and its act; note the evidence's id.
      |2. Call state_claim once: a one-sentence statement of what the document shows about the
      |   question, deriving from the evidence id you were given, with one stance of the form
      |   "<question>/<hypothesis> supports" or "<question>/<hypothesis> contradicts" on the
      |   question's hypothesis the document bears on. The tool answers with the claim held and
      |   its act.
      |3. Answer with JSON: {"document": <the document's id>, "evidence": <the evidence's id>,
      |   "claim": <the claim's id>, "excerpt": <the excerpt>, "statement": <the claim's
      |   statement>, "stances": [<the stances>]}, so the critique can read what you recorded.
      |
      |If a tool answers with an error, it is a refusal: a rule of the service the record would
      |break, as {"rule": ..., "error": ..., "names": {...}}. Do not try again and do not call
      |another tool. Stop and answer with JSON: {"document": <the document's id>,
      |"refused": {"rule": <the rule>, "names": <the names>}}.
      |
      |State only what the document says. Do not add what you know from elsewhere.""".stripMargin

  private def reviser(weighing: String): String =
    s"""You are the reviser of a holder's belief in a question's hypothesis.
       |
       |You are given the run's input (the tranche, the holder, the question and perhaps the belief
       |revision to follow), the verdict of the critique (the claims that passed), and what the
       |readers found for each document: the document, its evidence and its claim.
       |
       |Do exactly this:
       |1. Decide the holder's probability, from nought to one, that the question's "yes"
       |   hypothesis is true, given the claims that passed and the documents they came from.
       |   $weighing
       |2. Call revise_belief once, with the hypothesis "<question>/yes", the probability, and
       |   every claim that passed as a rest of the form "<claim id>@<weight>", the weight from
       |   nought to one being how much the claim moved you. The tool answers with the belief
       |   revision held and its act.
       |3. Answer with JSON: {"revision": <the belief revision's id>}.
       |
       |If the tool answers with an error, it is a refusal: a rule of the service the record would
       |break, as {"rule": ..., "error": ..., "names": {...}}. Do not try again. Stop and answer
       |with JSON: {"refused": {"rule": <the rule>, "names": <the names>}}.""".stripMargin

  /** Every document weighed by its kind; a rumour counts, but weakly. */
  val v1: Instructions = Instructions(
    V1,
    reader,
    reviser(
      "Weigh every document by its kind: an official document (a filing, a regulator's notice, an " +
        "approval) and a dated announcement count most, an analyst's view and a supplier's remarks " +
        "count somewhat, and a rumour counts, but weakly."
    )
  )

  /** An official document counts double and a rumour counts for nothing. */
  val v2: Instructions = Instructions(
    V2,
    reader,
    reviser(
      "Weigh an official document (a filing, a regulator's notice, an approval) double, a dated " +
        "announcement and a supplier's remarks once, an analyst's view a little, and a rumour as " +
        "nothing at all: give it a weight of nought and let it move you nowhere."
    )
  )

  val all: Vector[Instructions] = Vector(v1, v2)

  def named(name: String): Option[Instructions] = all.find(_.name == name)
