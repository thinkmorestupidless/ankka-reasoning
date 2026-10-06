package reasoning.support

import com.thinkmorestupidless.ankka.http.{Caller, LocalCallers}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

/** What the service answered: its status and its body, read as JSON when it is asked for. */
final case class Answer(status: Int, body: String):
  def json: ujson.Value = if body.isEmpty then ujson.Null else ujson.read(body)

  /** The refusal's rule, when the answer is a refusal. */
  def rule: String = json.obj.get("rule").map(_.str).getOrElse("")

  /** What a refusal names. */
  def names: Map[String, String] =
    json.obj.get("names").map(_.obj.view.mapValues(_.str).toMap).getOrElse(Map.empty)

/**
 * The service as a writer or a reader meets it: over HTTP and nothing else. A request is sent as
 * the service `test/<writer>`, which outside a cluster is how a test is more than one caller.
 */
final class Http(baseUrl: String):

  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  def post(path: String, body: ujson.Value, writer: String = Http.Writer): Answer =
    send("POST", path, Some(ujson.write(body)), writer)

  def get(path: String, writer: String = Http.Writer): Answer = send("GET", path, None, writer)

  def send(method: String, path: String, body: Option[String], writer: String): Answer =
    val builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(60))
    val (name, value) = LocalCallers.header(Caller.Service("test", writer))
    builder.header(name, value)
    body match
      case Some(json) =>
        builder.header("Content-Type", "application/json")
        builder.method(method, HttpRequest.BodyPublishers.ofString(json)): Unit
      case None => builder.method(method, HttpRequest.BodyPublishers.noBody()): Unit
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    Answer(response.statusCode, response.body)

object Http:
  /** The writer a step sends as when the scenario does not name one. */
  val Writer = "writer"

  /** The writer string the service makes of `test/<name>`. */
  def writer(name: String): String = s"service:test/$name"

  def encode(text: String): String = java.net.URLEncoder.encode(text, "UTF-8")
