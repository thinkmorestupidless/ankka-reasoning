package reasoning.belief.domain

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * A time, held as whole milliseconds since the epoch, which is how it reaches the graph, and
 * written in JSON as an ISO-8601 instant, which is how a writer sends it and reads it back.
 */
final case class Moment(millis: Long) extends Ordered[Moment]:
  def compare(that: Moment): Int = java.lang.Long.compare(millis, that.millis)
  def iso: String                = Instant.ofEpochMilli(millis).toString
  override def toString: String  = iso

object Moment:

  def parse(text: String): Option[Moment] =
    try Some(Moment(Instant.parse(text).toEpochMilli))
    catch case _: DateTimeParseException => None

  given codec: JsonValueCodec[Moment] = new JsonValueCodec[Moment]:
    def decodeValue(in: JsonReader, default: Moment): Moment =
      val text = in.readString(null)
      parse(text).getOrElse(in.decodeError(s"'$text' is not an ISO-8601 instant"))
    def encodeValue(value: Moment, out: JsonWriter): Unit = out.writeVal(value.iso)
    def nullValue: Moment                                 = null
