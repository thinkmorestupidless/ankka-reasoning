package reasoning.belief.api

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, writeToArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import com.thinkmorestupidless.ankka.core.{CommandError, ErrorCode}
import com.thinkmorestupidless.ankka.http.{Bytes, Respond}
import reasoning.belief.domain.{Refusal, Refused}
import reasoning.graph.GraphUnavailable

/**
 * What every route answers with: JSON, under a status the route chose.
 *
 * A handler's body runs inside `handle`, which turns a refusal into the body and status the
 * refusals contract describes. Everything a route returns is a `Reply`, so one route can answer
 * with a record or with a refusal.
 */
type Reply = Respond[Bytes]

object Replies:

  private val Json = "application/json"

  /** The codec configuration for everything the API writes: an absent value is left out. */
  inline def codec[A]: JsonValueCodec[A] =
    JsonCodecMaker.make[A](
      CodecMakerConfig
        .withDiscriminatorFieldName(Some("type"))
        .withAllowRecursiveTypes(true)
        .withTransientEmpty(false)
        .withTransientNone(true)
        .withTransientDefault(false)
    )

  private given JsonValueCodec[Refusal] = codec[Refusal]

  def json[A](status: Int, value: A)(using JsonValueCodec[A]): Reply =
    Respond(Bytes(Json, writeToArray(value)), status)

  def ok[A](value: A)(using JsonValueCodec[A]): Reply = json(200, value)

  /** `201` for a record that is new, `200` for the same record sent again. */
  def recorded[A](value: A, created: Boolean)(using JsonValueCodec[A]): Reply =
    json(if created then 201 else 200, value)

  def refused(refusal: Refusal): Reply = json(refusal.status, refusal)

  /** Runs a route's body, answering a refusal as the refusals contract says. */
  def handle(body: => Reply): Reply =
    try body
    catch
      case refused: Refused => Replies.refused(refused.refusal)
      case down: GraphUnavailable =>
        Replies.refused(
          Refusal(
            "graph.unavailable",
            503,
            s"The graph database cannot be read: ${down.getMessage}"
          )
        )
      case error: CommandError => Replies.refused(fromCommand(error))

  private def fromCommand(error: CommandError): Refusal =
    val status = error.code match
      case ErrorCode.BadRequest   => 400
      case ErrorCode.Unauthorized => 401
      case ErrorCode.Forbidden    => 403
      case ErrorCode.NotFound     => 404
      case ErrorCode.Conflict     => 409
      case ErrorCode.Timeout      => 504
      case ErrorCode.Unavailable  => 503
      case ErrorCode.Internal     => 500
    Refusal("command.refused", status, error.message)
