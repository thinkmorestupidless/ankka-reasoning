package reasoning.process.appraiser

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, JsonValueCodec}
import com.thinkmorestupidless.ankka.agent.Json
import reasoning.belief.api.Replies
import reasoning.belief.domain.{Moment, Stance}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.concurrent.TrieMap

/** What each instruction set makes of a document, or of a tranche: a number per set. */
final case class PerSet(v1: Double, v2: Double):
  def of(set: String): Double = set match
    case Instructions.V2 => v2
    case _               => v1

/**
 * What a document bears on and how: the hypothesis, the stance, a one-sentence statement a scripted
 * reader states as its claim, and the weight each instruction set gives the claim.
 */
final case class ExpectedReading(
    hypothesis: String,
    stance: String,
    statement: String,
    weight: PerSet
):
  /** The hypothesis's own id within its question: `launch/yes` is `yes`. */
  def hypothesisId: String = hypothesis.split('/').last

  /** `+1` for a document that supports the hypothesis, `-1` for one that contradicts it. */
  def sign: Int = if stance == Stance.Supports then 1 else -1

/** One made-up text a tranche holds, with its provenance and its expected reading. */
final case class Document(
    id: String,
    kind: String,
    source: String,
    locator: String,
    publishedAt: Moment,
    text: String,
    expectedReading: ExpectedReading
):
  /** The document as a reader is given it: its provenance and its text, and not its reading. */
  def json: Json = Json.obj(
    "id"          -> Json.str(id),
    "kind"        -> Json.str(kind),
    "source"      -> Json.str(source),
    "locator"     -> Json.str(locator),
    "publishedAt" -> Json.str(publishedAt.iso),
    "text"        -> Json.str(text)
  )

/**
 * The documents a run of the appraiser reads, dated in one month, and the probability each
 * instruction set's reviser states after reading them.
 */
final case class Tranche(
    name: String,
    month: String,
    probability: PerSet,
    documents: Vector[Document]
):

  def document(id: String): Option[Document] = documents.find(_.id == id)

  /**
   * The weighted reading of the tranche under an instruction set: one half, moved by each
   * document's weight in the direction of its stance. The scripted reviser states `probability`,
   * and the suite holds that the two agree.
   */
  def weightedReading(set: String): Double =
    0.5 + 0.5 * documents.map(d => d.expectedReading.sign * d.expectedReading.weight.of(set)).sum

final case class GoldenSource(id: String, name: String)
final case class GoldenHypothesis(id: String, statement: String)
final case class GoldenOutcome(outcome: String, hypothesis: String)

final case class GoldenMarket(
    id: String,
    venue: String,
    outcomes: Vector[GoldenOutcome],
    resolutionCriteria: String,
    closesAt: Moment
)

/** The launch question, the sources the documents come from, and the market about it. */
final case class GoldenQuestion(
    id: String,
    statement: String,
    hypotheses: Vector[GoldenHypothesis],
    sources: Vector[GoldenSource],
    market: GoldenMarket
)

/**
 * The files the example and the suites share: the launch question, two tranches of documents and
 * their expected readings. Read from a directory when the service is given one, and otherwise from
 * the class path, where the `process` module carries them. A file in the directory wins over the
 * one on the class path of the same name, so a suite can add a tranche of its own.
 */
final class GoldenSet(dir: Option[Path] = None):

  private val tranches = TrieMap.empty[String, Option[Tranche]]

  lazy val question: GoldenQuestion =
    read("question.json")
      .map(readFromString[GoldenQuestion](_)(using GoldenSet.questionCodec))
      .getOrElse(throw IllegalStateException("the golden set has no question.json"))

  /** A tranche by its name, which is its file's. */
  def tranche(name: String): Option[Tranche] =
    tranches.getOrElseUpdate(
      name,
      Option
        .when(GoldenSet.TrancheName.matches(name))(name)
        .flatMap(n => read(s"$n.json"))
        .map(readFromString[Tranche](_)(using GoldenSet.trancheCodec))
    )

  def document(tranche: String, id: String): Option[Document] =
    this.tranche(tranche).flatMap(_.document(id))

  private def read(file: String): Option[String] =
    dir
      .map(_.resolve(file))
      .filter(Files.isRegularFile(_))
      .map(Files.readString)
      .orElse(
        Option(getClass.getClassLoader.getResourceAsStream(file)).map { stream =>
          try String(stream.readAllBytes(), StandardCharsets.UTF_8)
          finally stream.close()
        }
      )

object GoldenSet:
  /** A tranche's name is a file's: letters, digits and hyphens. */
  val TrancheName = "^[a-z0-9][a-z0-9-]*$".r

  given trancheCodec: JsonValueCodec[Tranche]         = Replies.codec[Tranche]
  given questionCodec: JsonValueCodec[GoldenQuestion] = Replies.codec[GoldenQuestion]
