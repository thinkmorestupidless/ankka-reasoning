package reasoning.process.appraiser

import com.thinkmorestupidless.ankka.agent.Json
import com.thinkmorestupidless.ankka.agent.blueprint.*
import com.thinkmorestupidless.ankka.agent.judgment.{Question, YesNoQuestion}
import reasoning.belief.application.{RegisterHolder, RegisterSource}
import reasoning.belief.domain.Refused
import reasoning.process.ProcessConfig
import reasoning.process.application.ProcessRecorder
import reasoning.process.domain.Ids
import reasoning.process.tools.Tools

/**
 * The appraiser: the blueprint the service carries, as data. It reads each document of a tranche,
 * records it as evidence and states a claim; a judgment critiques every claim; and when the
 * critique passes a reviser revises the holder's belief in the question's `yes` hypothesis.
 */
object Appraiser:

  val Name = "appraiser"

  /** The judgment the critique asks of each claim. */
  val QuestionId = "claim-derives-from-tranche"

  val document: Shape = Shape.obj(
    "id"          -> Shape.string,
    "kind"        -> Shape.string,
    "source"      -> Shape.string,
    "locator"     -> Shape.string,
    "publishedAt" -> Shape.string,
    "text"        -> Shape.string
  )

  /**
   * The run's input: `tranche`, `holder` and `question`; `follows` and `observedAt` may be given.
   */
  val inputShape: Shape = Shape.obj(
    Seq(
      "tranche"    -> Shape.string,
      "holder"     -> Shape.string,
      "question"   -> Shape.string,
      "follows"    -> Shape.string,
      "observedAt" -> Shape.string
    ),
    required = Seq("tranche", "holder", "question")
  )

  /**
   * What a reader answers for one document: the ids of what it recorded, with the claim's words and
   * the excerpt for the critique to read; or the refusal it met.
   */
  val readItem: Shape = Shape.obj(
    Seq(
      "document"  -> Shape.string,
      "evidence"  -> Shape.string,
      "claim"     -> Shape.string,
      "excerpt"   -> Shape.string,
      "statement" -> Shape.string,
      "stances"   -> Shape.arr(Shape.string),
      "refused"   -> Shape.any
    ),
    required = Seq("document")
  )

  val verdict: Shape = Shape.obj("passed" -> Shape.boolean, "claims" -> Shape.arr(Shape.string))

  /** What the reviser answers: the revision's id, or the refusal it met. */
  val revised: Shape =
    Shape.obj(Seq("revision" -> Shape.string, "refused" -> Shape.any), required = Seq.empty)

  def blueprint(instructions: Instructions): Blueprint =
    Blueprint(Name)
      .input(inputShape)
      .worker(
        Worker("reader")
          .instructions(instructions.reader)
          .tools(Tools.RecordEvidence, Tools.StateClaim)
          .budget(6)
      )
      .worker(
        Worker("reviser").instructions(instructions.reviser).tools(Tools.ReviseBelief).budget(4)
      )
      .step(Step("load").call("load-tranche").reads("input").result(Shape.arr(document)))
      .step(Step("read").forEach("reader", "load").reads("input").result(Shape.arr(readItem)))
      .step(Step("critique").judge(QuestionId).each("read").reads("load"))
      .step(Step("verdict").call("critique-verdict").reads("critique", "read").result(verdict))
      .step(Step("revise").ask("reviser").reads("verdict", "read", "input").result(revised))
      .step(
        Step("close")
          .call("revision-check")
          .reads("revise")
          .result(Shape.obj("revision" -> Shape.string))
      )

  val question: YesNoQuestion = Question.yesNo(
    QuestionId,
    """You are given one claim a reader stated (its statement and its stances), the evidence it
      |derives from (the document it was recorded from, and the excerpt), and the documents of this
      |run's tranche. Answer yes only when both of these hold:
      |1. The claim derives from evidence recorded from a document of this run's tranche, whoever
      |   recorded the evidence and whenever: the evidence's document is one of the tranche's
      |   documents, and the claim's statement follows from the excerpt.
      |2. Every stance of the claim is on a hypothesis of the run's question.
      |Otherwise answer no.""".stripMargin
  )

  /** The handlers the appraiser's call steps name. */
  def handlers(goldenSet: GoldenSet): Seq[BlueprintHandler] = Seq(
    BlueprintHandler("load-tranche") { (_, reads) =>
      val name = reads("input").flatMap(_("tranche")).flatMap(_.asString).getOrElse("")
      goldenSet
        .tranche(name)
        .fold(throw IllegalArgumentException(s"no tranche $name"))(tranche =>
          Json.Arr(tranche.documents.map(_.json))
        )
    },
    BlueprintHandler("critique-verdict") { (_, reads) =>
      val items     = reads("read").flatMap(_.asArray).getOrElse(Vector.empty)
      val judgments = reads("critique").flatMap(_.asArray).getOrElse(Vector.empty)
      val claims = items.zipWithIndex.map { (item, i) =>
        item("refused")
          .filterNot(_.isNull)
          .foreach(r => fail(s"the reader was refused: ${refusal(r)}"))
        val claim = item("claim")
          .flatMap(_.asString)
          .getOrElse(fail(s"item ${i + 1}: the reader answered with no claim"))
        val answer = judgments
          .lift(i)
          .flatMap(j => j(QuestionId).flatMap(_("answer")))
          .flatMap(_.asString)
        if !answer.contains("yes") then
          fail(
            s"claim $claim does not derive from evidence recorded from a document of the tranche"
          )
        Json.str(claim)
      }
      Json.obj("passed" -> Json.bool(true), "claims" -> Json.Arr(claims))
    },
    BlueprintHandler("revision-check") { (_, reads) =>
      val revise = reads("revise").getOrElse(Json.Null)
      revise("refused")
        .filterNot(_.isNull)
        .foreach(r => fail(s"the reviser was refused: ${refusal(r)}"))
      val id = revise("revision")
        .flatMap(_.asString)
        .getOrElse(fail("the reviser answered with no revision"))
      Json.obj("revision" -> Json.str(id))
    }
  )

  /** `<rule>, naming <k>=<v>, …`, as a reader or a reviser answered a refusal. */
  private def refusal(json: Json): String =
    val rule = json("rule").flatMap(_.asString).getOrElse("an unnamed rule")
    val names = json("names") match
      case Some(Json.Obj(fields)) =>
        fields.toVector.sortBy(_._1).map((k, v) => s"$k=${v.asString.getOrElse(v.render)}")
      case _ => Vector.empty
    if names.isEmpty then rule else s"$rule, naming ${names.mkString(", ")}"

  /** A handler fails the run: ankka ends it `failed`, with the message after the handler's name. */
  private def fail(message: String): Nothing = throw IllegalStateException(message)

  /**
   * What the service registers for its blueprints: the tools, the handlers, the question, and v1.
   */
  def registry(tools: Tools, goldenSet: GoldenSet): BlueprintRegistry =
    BlueprintRegistry.empty
      .tools(tools.all*)
      .handlers(handlers(goldenSet)*)
      .questions(question)
      .carrying(blueprint(Instructions.v1))

  /**
   * Registers the appraiser's holders and the golden set's sources, as the service starts; each is
   * a repeat after the first start. An id held by another record is left to it: a run for that
   * holder is refused when it writes.
   */
  def prepare(recorder: ProcessRecorder, goldenSet: GoldenSet, config: ProcessConfig): Unit =
    def leaving(write: => Unit): Unit =
      try write
      catch case refused: Refused if refused.refusal.rule == "id.held-by-another" => ()
    config.appraiserHolders.foreach { id =>
      leaving(
        recorder.belief
          .registerHolder(RegisterHolder(id, "agent", id), Ids.blueprintWriter(Name)): Unit
      )
    }
    goldenSet.question.sources.foreach { source =>
      leaving(
        recorder.belief
          .registerSource(RegisterSource(source.id, source.name), config.serviceWriter): Unit
      )
    }
