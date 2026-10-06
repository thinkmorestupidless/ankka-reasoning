package reasoning.seed

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path, Paths}
import java.time.Duration
import scala.collection.mutable

/** One record a seed file sent, and what the service answered. */
final case class Sent(path: String, status: Int, body: String):
  def refused: Boolean = status >= 400
  def created: Boolean = status == 201

/**
 * Posts a seed file's records, in order, to a running service: the first writer that is not a test,
 * and what a later agent will be.
 *
 * A seed file is `{"steps": [{"post": "/path", "body": {...}, "as": "name"}, ...]}`. A step with
 * `as` has the `id` of the record it is answered with remembered under that name, and a later step
 * refers to it as the string `"@name"`. That is how a claim names evidence, whose identifier is the
 * digest of what it says and is not known until it is recorded.
 */
object Seed:

  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  /** Every step of a file, sent; stops at the first refusal, which is the last element. */
  def run(file: Path, baseUrl: String, headers: Map[String, String] = Map.empty): Vector[Sent] =
    send(steps(file), baseUrl, headers)

  def steps(file: Path): Vector[ujson.Value] =
    ujson.read(Files.readString(file))("steps").arr.toVector

  /**
   * Steps sent in order, stopping at the first refusal. `each` is told of every step as it is
   * answered, with the step as it was sent: its names resolved.
   */
  def send(
      steps: Vector[ujson.Value],
      baseUrl: String,
      headers: Map[String, String] = Map.empty,
      each: (ujson.Value, Sent) => Unit = (_, _) => ()
  ): Vector[Sent] =
    val names    = mutable.Map.empty[String, String]
    val sent     = Vector.newBuilder[Sent]
    var stopped  = false
    val iterator = steps.iterator
    while !stopped && iterator.hasNext do
      val step = iterator.next()
      val path = resolve(ujson.Str(step("post").str), names).str
      val body = resolve(step("body"), names)
      val one  = post(baseUrl, path, ujson.write(body), headers)
      sent += one
      if one.refused then stopped = true
      else step.obj.get("as").foreach(name => names(name.str) = ujson.read(one.body)("id").str)
      each(ujson.Obj("post" -> path, "body" -> body), one)
    sent.result()

  /**
   * The record a step made, as `/graph/wait` names it: its kind and its identifier. A price
   * observation is a belief revision, and a resolution is published with its market.
   */
  def recordOf(path: String, body: ujson.Value, reply: ujson.Value): Option[(String, String)] =
    path.split('/').toList.drop(1) match
      case "questions" :: Nil              => Some("question" -> body("id").str)
      case "questions" :: question :: _    => Some("question" -> question)
      case "holders" :: Nil                => Some("holder" -> body("id").str)
      case "holders" :: holder :: _        => Some("holder" -> holder)
      case "sources" :: Nil                => Some("source" -> body("id").str)
      case "evidence" :: Nil               => Some("evidence" -> reply("id").str)
      case "evidence" :: evidence :: _     => Some("evidence" -> evidence)
      case "claims" :: Nil                 => Some("claim" -> body("id").str)
      case "claims" :: claim :: _          => Some("claim" -> claim)
      case "beliefs" :: "revisions" :: Nil => Some("revision" -> body("id").str)
      case "markets" :: Nil                => Some("market" -> body("id").str)
      case "markets" :: _ :: "price-observations" :: Nil =>
        Some("revision" -> reply("revision")("id").str)
      case "markets" :: market :: _ => Some("market" -> market)
      case _                        => None

  private def resolve(value: ujson.Value, names: mutable.Map[String, String]): ujson.Value =
    value match
      case ujson.Str(text) if text.startsWith("@") =>
        ujson.Str(
          names.getOrElse(
            text.drop(1),
            throw IllegalArgumentException(s"no earlier step is named '${text.drop(1)}'")
          )
        )
      case ujson.Str(text) if text.contains("{@") =>
        ujson.Str("\\{@([A-Za-z0-9._-]+)\\}".r.replaceAllIn(text, m => names(m.group(1))))
      case ujson.Arr(items) => ujson.Arr.from(items.map(resolve(_, names)))
      case ujson.Obj(items) => ujson.Obj.from(items.map((k, v) => k -> resolve(v, names)))
      case other            => other

  private def post(
      baseUrl: String,
      path: String,
      body: String,
      headers: Map[String, String]
  ): Sent =
    val builder = HttpRequest
      .newBuilder(URI.create(baseUrl + path))
      .timeout(Duration.ofSeconds(60))
      .header("Content-Type", "application/json")
    headers.foreach((name, value) => builder.header(name, value))
    val response = client.send(
      builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
      HttpResponse.BodyHandlers.ofString()
    )
    Sent(path, response.statusCode, response.body)

  def main(args: Array[String]): Unit =
    if args.length < 1 then
      System.err.println("usage: Seed <file> [base url]")
      sys.exit(2)
    val baseUrl = if args.length > 1 then args(1) else "http://localhost:9000"
    val sent    = run(Paths.get(args(0)), baseUrl)
    sent.foreach(s => println(f"${s.status}%3d ${s.path}%-28s ${if s.refused then s.body else ""}"))
    val created = sent.count(_.created)
    println(s"${sent.size} sent, $created new, ${sent.count(_.status == 200)} already held")
    if sent.lastOption.exists(_.refused) then sys.exit(1)
