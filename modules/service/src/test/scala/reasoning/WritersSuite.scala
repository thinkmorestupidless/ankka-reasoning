package reasoning

import com.thinkmorestupidless.ankka.http.{Caller, LocalCallers}
import reasoning.service.Writers
import reasoning.support.ServiceFixture

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.concurrent.duration.DurationInt

/** Who a caller is taken to be, as a writer: decided from the connection and nothing it says. */
class WritersSuite extends munit.FunSuite:

  override val munitTimeout = 5.minutes

  private val client = HttpClient.newHttpClient()

  /** Registers a source as a caller and answers with the status and the writer it was held with. */
  private def registerAs(id: String, header: Option[(String, String)]): (Int, String) =
    val builder = HttpRequest
      .newBuilder(URI.create(s"${ServiceFixture.baseUrl}/sources"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(s"""{"id":"$id","name":"a source"}"""))
    header.foreach((name, value) => builder.header(name, value))
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    val writer = if response.statusCode == 201 then ujson.read(response.body)("writer").str else ""
    (response.statusCode, writer)

  test("a caller is named as a writer, or not at all") {
    assertEquals(Writers.of(Caller.Service("shop", "forecaster")), Some("service:shop/forecaster"))
    assertEquals(Writers.of(Caller.Local), Some("local"))
    assertEquals(Writers.of(Caller.Gateway), None)
  }

  test("another service is the writer its certificate names, and two services are two writers") {
    val a = registerAs("writers-a", Some(LocalCallers.header(Caller.Service("shop", "a"))))
    val b = registerAs("writers-b", Some(LocalCallers.header(Caller.Service("shop", "b"))))
    assertEquals(a, (201, "service:shop/a"))
    assertEquals(b, (201, "service:shop/b"))
  }

  test("outside a cluster a caller with no certificate is local") {
    assertEquals(registerAs("writers-local", None), (201, "local"))
  }

  test("a caller through the gateway is refused with 401 until a person can be identified") {
    val (status, _) = registerAs("writers-gateway", Some(LocalCallers.header(Caller.Gateway)))
    assertEquals(status, 401)
    assertEquals(ServiceFixture.http.get("/sources/writers-gateway").status, 404)
  }

  test("a writer named in the request's body is ignored") {
    val response = client.send(
      HttpRequest
        .newBuilder(URI.create(s"${ServiceFixture.baseUrl}/sources"))
        .header("Content-Type", "application/json")
        .POST(
          HttpRequest.BodyPublishers.ofString(
            """{"id":"writers-body","name":"a source","writer":"service:shop/someone-else"}"""
          )
        )
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(response.statusCode, 201)
    assertEquals(ujson.read(response.body)("writer").str, "local")
  }
