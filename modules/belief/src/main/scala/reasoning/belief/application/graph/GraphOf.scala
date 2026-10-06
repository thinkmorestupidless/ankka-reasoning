package reasoning.belief.application.graph

import com.thinkmorestupidless.ankka.core.graph.PropertyValue
import reasoning.belief.BeliefVocabulary as V
import reasoning.belief.domain.*
import reasoning.graph.{Element, Elements}

/**
 * What each record of the belief layer is in the graph: its node, and every edge it stated.
 *
 * One function per kind of record, used by that record's graph consumer and by whoever waits for
 * the graph to hold it. Each edge runs from the record's own node to the node of a record that was
 * held before it, and the record is the only writer of everything it returns.
 */
object GraphOf:

  def hypothesisNode(reference: String): String = V.Hypothesis.id(reference)

  private def some(pairs: (String, Option[PropertyValue])*): Map[String, PropertyValue] =
    pairs.collect { case (name, Some(value)) => name -> value }.toMap

  /** A question, each of its hypotheses, and the edge from each to it. */
  def question(question: Question): Vector[Element] =
    val node = Elements.node(
      V.Question,
      question.id,
      question.dated.millis,
      question.recordedAt.millis,
      Map("statement" -> question.statement)
    )
    val rest = question.hypotheses.flatMap { hypothesis =>
      val reference = s"${question.id}/${hypothesis.id}"
      Vector(
        Elements.node(
          V.Hypothesis,
          reference,
          hypothesis.dated.millis,
          hypothesis.recordedAt.millis,
          Map("statement" -> hypothesis.statement)
        ),
        Elements.edge(V.Answers, V.Hypothesis.id(reference), node.id)
      )
    }
    node +: rest

  def holder(holder: Holder): Vector[Element] =
    Vector(
      Elements.node(
        V.Holder,
        holder.id,
        holder.dated.millis,
        holder.recordedAt.millis,
        Map("kind" -> holder.kind, "name" -> holder.name)
      )
    )

  def source(source: Source): Vector[Element] =
    Vector(
      Elements.node(
        V.Source,
        source.id,
        source.dated.millis,
        source.recordedAt.millis,
        Map("name" -> source.name)
      )
    )

  /** Evidence and the edge to its source. Withdrawn, it has no locator, excerpt or author. */
  def evidence(evidence: Evidence): Vector[Element] =
    val node = Elements.node(
      V.Evidence,
      evidence.id,
      evidence.dated.millis,
      evidence.recordedAt.millis,
      some(
        "locator"     -> evidence.locator,
        "excerpt"     -> evidence.excerpt,
        "author"      -> evidence.author,
        "publishedAt" -> evidence.publishedAt.map(_.millis),
        "withdrawn"   -> Some(evidence.withdrawn),
        "withdrawnAt" -> evidence.withdrawal.map(_.at.millis)
      )
    )
    Vector(node, Elements.edge(V.FromSource, node.id, V.Source.id(evidence.source)))

  /**
   * A claim and the edges to its holder, its evidence, the hypotheses it bears on and the claim it
   * revises.
   */
  def claim(claim: Claim): Vector[Element] =
    val node = Elements.node(
      V.Claim,
      claim.id,
      claim.dated.millis,
      claim.recordedAt.millis,
      some(
        "statement"   -> claim.statement,
        "withdrawn"   -> Some(claim.withdrawn),
        "withdrawnAt" -> claim.withdrawal.map(_.at.millis)
      )
    )
    val stances = claim.stances.map { stance =>
      val kind = if stance.stance == Stance.Supports then V.Supports else V.Contradicts
      Elements.edge(kind, node.id, hypothesisNode(stance.hypothesis))
    }
    Vector(node, Elements.edge(V.StatedBy, node.id, V.Holder.id(claim.holder))) ++
      claim.derivesFrom.map(evidence =>
        Elements.edge(V.DerivesFrom, node.id, V.Evidence.id(evidence))
      ) ++
      stances ++
      claim.revises.map(revised => Elements.edge(V.Revises, node.id, V.Claim.id(revised)))

  /**
   * A belief revision and the edges to its holder, its hypothesis, the claims it rests on and the
   * revision it follows.
   */
  def revision(revision: Revision): Vector[Element] =
    val node = Elements.node(
      V.BeliefRevision,
      revision.id,
      revision.dated.millis,
      revision.recordedAt.millis,
      Map("n" -> revision.n, "probability" -> revision.probability)
    )
    Vector(
      node,
      Elements.edge(V.HeldBy, node.id, V.Holder.id(revision.holder)),
      Elements.edge(V.BeliefIn, node.id, hypothesisNode(revision.hypothesis))
    ) ++
      revision.restsOn.map(rest =>
        Elements.edge(V.RestsOn, node.id, V.Claim.id(rest.claim), some("weight" -> rest.weight))
      ) ++
      revision.follows.map(before => Elements.edge(V.Follows, node.id, V.BeliefRevision.id(before)))
