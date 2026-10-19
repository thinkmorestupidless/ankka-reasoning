package reasoning.process.tools

import com.github.plokhotnyuk.jsoniter_scala.core.{writeToString, JsonValueCodec}
import com.thinkmorestupidless.ankka.agent.{FunctionTool, Json}
import reasoning.belief.api.BeliefJson.given
import reasoning.belief.api.Replies
import reasoning.belief.application.{RecordEvidence, StateBelief, StateClaim}
import reasoning.belief.domain.{HypothesisRef, Moment, Refusal, Refused, Rest, Rules, Stance}
import reasoning.process.api.ProcessJson.given
import reasoning.process.appraiser.GoldenSet
import reasoning.process.application.ProcessRecorder
import reasoning.process.domain.*

/**
 * A refusal, on its way to the model: a tool's error is a string, and this one's is the refusal as
 * the HTTP API would answer it, `{rule, error, names}`, so the worker can stop and say which rule.
 */
final class ToolRefused(val refusal: Refusal) extends RuntimeException(ToolRefused.render(refusal)):
  override def fillInStackTrace(): Throwable = this

object ToolRefused:
  private given JsonValueCodec[Refusal] = Replies.codec[Refusal]
  def render(refusal: Refusal): String  = writeToString(refusal)

/**
 * The tools any blueprint of this service may name. Each writes the record a writer would send, as
 * the holder the run's input names and the writer `blueprint:<name>`, dated by the input's
 * `observedAt` or the clock, then its act; and is refused exactly as a writer is.
 */
final class Tools(resolver: RunResolver, recorder: ProcessRecorder, goldenSet: GoldenSet):

  val recordEvidence: FunctionTool =
    FunctionTool
      .named(Tools.RecordEvidence)
      .describedAs(
        "Records evidence from one document of the run's tranche: a quoted excerpt of its text. " +
          "Answers with the evidence held and its act, or with a refusal naming the rule."
      )
      .param[String]("document", "the id of a document of the run's tranche")
      .param[String]("excerpt", "a passage of the document's text, quoted as written")
      .param[Option[String]]("author", "the document's author, when it names one")
      .handle { (document, excerpt, author) =>
        refusing {
          val r      = resolver.resolve(Tools.RecordEvidence)
          val writer = spokenFor(r)
          val doc = goldenSet
            .document(r.input.tranche, document)
            .getOrElse {
              Rules.require(ProcessRules.documentNotInTranche(document, r.input.tranche, false))
              throw IllegalStateException("unreachable")
            }
          val evidence = recorder.belief
            .recordEvidence(
              RecordEvidence(
                doc.source,
                doc.locator,
                excerpt,
                author,
                Some(doc.publishedAt),
                r.input.observedAt
              ),
              writer
            )
            .record
          val act = hold(r, RecordRef(RecordRef.Evidence, evidence.id), evidence.dated, writer)
          Json.obj("evidence" -> json(evidence), "act" -> json(act))
        }
      }

  val stateClaim: FunctionTool =
    FunctionTool
      .named(Tools.StateClaim)
      .describedAs(
        "States a claim as the run's holder: one sentence, deriving from evidence already recorded, " +
          "with a stance on a hypothesis. Answers with the claim held and its act, or with a " +
          "refusal naming the rule."
      )
      .param[String]("statement", "one sentence: what the evidence shows")
      .param[List[String]]("derivesFrom", "the ids of the evidence the claim derives from")
      .param[List[String]](
        "stances",
        "each '<question>/<hypothesis> supports' or '<question>/<hypothesis> contradicts'"
      )
      .handle { (statement, derivesFrom, stances) =>
        refusing {
          val r      = resolver.resolve(Tools.StateClaim)
          val writer = spokenFor(r)
          val id     = Ids.claim(r.run.id, statement)
          val claim = recorder.belief
            .stateClaim(
              StateClaim(
                id,
                r.input.holder,
                statement,
                derivesFrom.toVector,
                stances.toVector.map(Tools.stance),
                None,
                r.input.observedAt
              ),
              writer
            )
            .record
          val act = hold(r, RecordRef(RecordRef.Claim, claim.id), claim.dated, writer)
          Json.obj("claim" -> json(claim), "act" -> json(act))
        }
      }

  val reviseBelief: FunctionTool =
    FunctionTool
      .named(Tools.ReviseBelief)
      .describedAs(
        "Revises the run's holder's belief in a hypothesis: a probability, resting on claims the " +
          "holder stated, following the revision the run's input names. Answers with the belief " +
          "revision held and its act, or with a refusal naming the rule."
      )
      .param[String]("hypothesis", "'<question>/<hypothesis>'")
      .param[Double]("probability", "from nought to one")
      .param[List[String]]("restsOn", "each '<claim id>' or '<claim id>@<weight>'")
      .handle { (hypothesis, probability, restsOn) =>
        refusing {
          val r      = resolver.resolve(Tools.ReviseBelief)
          val writer = spokenFor(r)
          val ref = HypothesisRef
            .parse(hypothesis)
            .getOrElse(throw Rules.notHeld("hypothesis", hypothesis))
          val id    = Ids.revision(r.run.id, ref)
          val rests = restsOn.toVector.map(Tools.rest)
          rests.foreach { rest =>
            recorder.belief.findClaim(rest.claim).filter(_.holder != r.input.holder).foreach {
              other =>
                throw new Refused(ProcessRules.restsOnOtherHolder(id, other.id, r.input.holder))
            }
          }
          val revision = recorder.belief
            .stateBelief(
              StateBelief(
                id,
                r.input.holder,
                hypothesis,
                probability,
                rests,
                r.input.follows,
                r.input.observedAt
              ),
              writer
            )
            .record
          val act = hold(r, RecordRef(RecordRef.Revision, revision.id), revision.dated, writer)
          Json.obj("revision" -> json(revision), "act" -> json(act))
        }
      }

  val all: Seq[FunctionTool] = Seq(recordEvidence, stateClaim, reviseBelief)

  /** The writer the run writes as, once the holder is held and the blueprint speaks for it. */
  private def spokenFor(r: Resolved): String =
    val writer = Ids.blueprintWriter(r.run.blueprint)
    Rules.require(Rules.speaksFor(recorder.belief.holder(r.input.holder), writer))
    writer

  private def hold(r: Resolved, record: RecordRef, dated: Moment, writer: String): Act =
    recorder
      .holdAct(
        Act(
          Ids.act(record),
          r.run.id,
          r.ref.step,
          r.worker,
          r.run.blueprint,
          r.run.version,
          record,
          r.input.holder,
          dated,
          recorder.now(),
          writer
        )
      )
      .record

  private def refusing(body: => Json): Json =
    try body
    catch case refused: Refused => throw ToolRefused(refused.refusal)

  private def json[A](value: A)(using JsonValueCodec[A]): Json =
    Json.parse(writeToString(value)).fold(e => throw IllegalStateException(e), identity)

object Tools:
  val RecordEvidence = "record_evidence"
  val StateClaim     = "state_claim"
  val ReviseBelief   = "revise_belief"

  /** `<question>/<hypothesis> supports`, or whatever the belief layer refuses as a stance. */
  def stance(text: String): Stance = text.trim.lastIndexOf(' ') match
    case -1 => Stance(text.trim, "")
    case at => Stance(text.trim.take(at).trim, text.trim.drop(at + 1))

  /** `<claim>` or `<claim>@<weight>`; anything else is `belief.rest.format`. */
  def rest(text: String): Rest = text.split('@') match
    case Array(claim) if claim.nonEmpty => Rest(claim, None)
    case Array(claim, weight) if claim.nonEmpty =>
      weight.toDoubleOption.fold(throw new Refused(ProcessRules.restFormat(text)))(w =>
        Rest(claim, Some(w))
      )
    case _ => throw new Refused(ProcessRules.restFormat(text))
