package reasoning.steps

import com.thinkmorestupidless.ankka.agent.{
  ChatMessage,
  Json,
  ModelRequest,
  TestModelProvider,
  ToolResult
}
import com.thinkmorestupidless.ankka.agent.judgment.{Answers, TestJudgmentProvider}
import com.thinkmorestupidless.ankka.testkit.blueprint.Scripted
import reasoning.process.appraiser.{Appraiser, Document, GoldenSet, Instructions}
import reasoning.process.tools.Tools

/**
 * A model that drives the appraiser from the golden set: a reader records the document's opening
 * words and states its expected reading; the reviser states the tranche's probability for the
 * instruction set, resting on every claim with its weight; the judgment says yes. A scenario
 * installs it, then overrides what it sets out to break.
 */
final class ScriptedAppraiser(
    model: TestModelProvider,
    judge: TestJudgmentProvider,
    goldenSet: GoldenSet
):

  /** How many of the document's words the excerpt is: a scenario's own, so its evidence is. */
  var excerptWords: Int = 12

  var instructions: String = Instructions.V1

  /** What the claim derives from, instead of the evidence the reader just recorded. */
  var derivesFrom: Option[Vector[String]] = None

  /** The stance the reader states, instead of the document's expected one. */
  var stance: Option[String] = None

  /** What the revision rests on, instead of every claim that passed, with its weight. */
  var restsOn: Option[Vector[String]] = None

  var probability: Option[Double] = None

  def excerpt(document: Document): String =
    document.text.split("\\s+").take(excerptWords).mkString(" ")

  def statement(document: Document): String = document.expectedReading.statement

  def weight(document: Document): Double = document.expectedReading.weight.of(instructions)

  /** Rests the revision on this evidence's claim instead: the claim derives from it. */
  def withClaimFrom(evidence: String): Unit = derivesFrom = Some(Vector(evidence))

  /** The judgment answers no for this document's claim, in the next run of the tranche. */
  def judgeNo(tranche: String, document: String): Unit =
    val documents = goldenSet.tranche(tranche).map(_.documents).getOrElse(Vector.empty)
    judge.expect(
      documents.map(d =>
        Answers.yesNo(Appraiser.question, if d.id == document then 0.05 else 0.95)
      )*
    ): Unit

  def install(): Unit =
    judge.always(Answers.yesNo(Appraiser.question, 0.95)): Unit

    model.respondWhen(r => Scripted.forStep(r, "read") && !Scripted.afterTool(r)) { r =>
      val (_, document) = reading(r)
      Scripted.call(
        Tools.RecordEvidence,
        Json.obj("document" -> Json.str(document.id), "excerpt" -> Json.str(excerpt(document)))
      )
    }

    model.respondWhen(r => Scripted.forStep(r, "read") && Scripted.afterTool(r)) { r =>
      val (input, document) = reading(r)
      val results           = toolResults(r)
      val last              = results.last
      if last.isError then
        Scripted.answer(
          Json.obj("document" -> Json.str(document.id), "refused" -> refusalOf(last.content)).render
        )
      else if last.name == Tools.RecordEvidence then
        val evidence = idIn(last.content, "evidence")
        val question = input("question").flatMap(_.asString).getOrElse("launch")
        Scripted.call(
          Tools.StateClaim,
          Json.obj(
            "statement"   -> Json.str(statement(document)),
            "derivesFrom" -> Json.Arr(derivesFrom.getOrElse(Vector(evidence)).map(Json.str)),
            "stances" -> Json.arr(
              Json.str(
                stance.getOrElse(
                  s"$question/${document.expectedReading.hypothesisId} ${document.expectedReading.stance}"
                )
              )
            )
          )
        )
      else
        val evidence = results
          .find(t => t.name == Tools.RecordEvidence && !t.isError)
          .map(t => idIn(t.content, "evidence"))
          .getOrElse("")
        Scripted.answer(
          Json
            .obj(
              "document"  -> Json.str(document.id),
              "evidence"  -> Json.str(evidence),
              "claim"     -> Json.str(idIn(last.content, "claim")),
              "excerpt"   -> Json.str(excerpt(document)),
              "statement" -> Json.str(statement(document)),
              "stances"   -> Json.arr(Json.str(stance.getOrElse(document.expectedReading.stance)))
            )
            .render
        )
    }

    model.respondWhen(r => Scripted.forStep(r, "revise") && !Scripted.afterTool(r)) { r =>
      val step     = stepJson(r)
      val input    = step("input").getOrElse(Json.Null)
      val tranche  = input("tranche").flatMap(_.asString).getOrElse("")
      val question = input("question").flatMap(_.asString).getOrElse("launch")
      val items    = step("read").flatMap(_.asArray).getOrElse(Vector.empty)
      val rests = restsOn.getOrElse(
        items.flatMap { item =>
          for
            claim    <- item("claim").flatMap(_.asString)
            document <- item("document").flatMap(_.asString).flatMap(goldenSet.document(tranche, _))
          yield s"$claim@${weight(document)}"
        }
      )
      val p = probability.getOrElse(
        goldenSet.tranche(tranche).map(_.probability.of(instructions)).getOrElse(0.5)
      )
      Scripted.call(
        Tools.ReviseBelief,
        Json.obj(
          "hypothesis"  -> Json.str(s"$question/yes"),
          "probability" -> Json.num(p),
          "restsOn"     -> Json.Arr(rests.map(Json.str))
        )
      )
    }

    model.respondWhen(r => Scripted.forStep(r, "revise") && Scripted.afterTool(r)) { r =>
      val last = toolResults(r).last
      if last.isError then Scripted.answer(Json.obj("refused" -> refusalOf(last.content)).render)
      else Scripted.answer(Json.obj("revision" -> Json.str(idIn(last.content, "revision"))).render)
    }: Unit

  // ── Reading a request ─────────────────────────────────────────────────────

  /** The JSON after the step's first line. */
  private def stepJson(r: ModelRequest): Json =
    Scripted
      .userText(r)
      .map(_.dropWhile(_ != '\n').drop(1))
      .flatMap(Json.parse(_).toOption)
      .getOrElse(Json.Null)

  private def reading(r: ModelRequest): (Json, Document) =
    val step    = stepJson(r)
    val input   = step("input").getOrElse(Json.Null)
    val tranche = input("tranche").flatMap(_.asString).getOrElse("")
    val id      = step("item").flatMap(_("id")).flatMap(_.asString).getOrElse("")
    (
      input,
      goldenSet
        .document(tranche, id)
        .getOrElse(throw IllegalStateException(s"no document $tranche/$id"))
    )

  private def toolResults(r: ModelRequest): Vector[ToolResult] =
    r.messages.collect { case ChatMessage.ToolResults(results) => results }.flatten

  private def idIn(content: String, kind: String): String =
    Json.parse(content).toOption.flatMap(_(kind)).flatMap(_("id")).flatMap(_.asString).getOrElse("")

  /** The tool's error as the reader answers it: `{rule, names}`. */
  private def refusalOf(content: String): Json =
    Json.parse(content).toOption match
      case Some(json) if json("rule").isDefined =>
        Json.obj("rule" -> json("rule").get, "names" -> json("names").getOrElse(Json.obj()))
      case _ => Json.obj("rule" -> Json.str(content), "names" -> Json.obj())
