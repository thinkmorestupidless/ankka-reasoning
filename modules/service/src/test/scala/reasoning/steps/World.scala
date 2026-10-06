package reasoning.steps

import reasoning.support.Answer

import scala.collection.mutable

/**
 * What one scenario knows: the identifiers of the records it made, under a prefix of its own, and
 * what the service last answered. A new one for every scenario.
 */
final class World(val p: String):

  /**
   * The answer to the last request a step made, and to each request of a step that made several.
   */
  var last: Answer            = Answer(0, "")
  var answers: Vector[Answer] = Vector.empty

  /** The rule the scenario's `When` set out to break, when it did. */
  var breaks: String = ""

  /** The last write, so that "the same again" is exactly that. */
  var sentPath: String      = ""
  var sentBody: ujson.Value = ujson.Null
  var firstAnswer: Answer   = Answer(0, "")

  var question: String      = ""
  var otherQuestion: String = ""

  /**
   * Hypotheses by their statement, as a step names them: statement -> `<question>/<hypothesis>`.
   */
  val hypotheses: mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty

  /** The hypothesis a scenario calls "the hypothesis". */
  var hypothesis: String = ""

  var holder: String         = ""
  var otherHolder: String    = ""
  var source: String         = ""
  var evidence: String       = ""
  var claim: String          = ""
  var laterClaim: String     = ""
  var foreignClaim: String   = ""
  var claims: Vector[String] = Vector.empty

  var revision: String          = ""
  var revisions: Vector[String] = Vector.empty

  /** Records a step made that a later step may need the graph to hold: kind and identifier. */
  var published: Vector[(String, String)] = Vector.empty

  var market: String      = ""
  var otherMarket: String = ""

  /** "that writer", when a scenario speaks of one who is not the writer of everything else. */
  var thatWriter: String = "writer"

  /** Whatever else a step wants a later step to find. */
  val notes: mutable.Map[String, String] = mutable.Map.empty

  private var counter = 0

  /** An identifier no other record of this scenario has. */
  def fresh(what: String): String =
    counter += 1
    s"$p-$what-$counter"

object World:

  private val Months = Vector(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
  )

  /** "4 May" as an instant of 2026, at nine in the morning. */
  def date(day: String): String = day.split(' ') match
    case Array(d, month) if Months.contains(month) =>
      f"2026-${Months.indexOf(month) + 1}%02d-${d.toInt}%02dT09:00:00Z"
    case _ => throw IllegalArgumentException(s"'$day' is not a day and a month")

  /** The same day at noon: a "today" that every time of that day's morning is before. */
  def noon(day: String): String = date(day).replace("T09:00:00Z", "T12:00:00Z")
