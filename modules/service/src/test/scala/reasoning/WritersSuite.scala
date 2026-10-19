package reasoning

import com.thinkmorestupidless.ankka.http.{Caller, LocalCallers}
import reasoning.service.Writers
import reasoning.support.{People, ServiceFixture}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.concurrent.duration.DurationInt

/** Who a caller is taken to be, as a writer: decided from the connection and nothing it says. */
class WritersSuite extends munit.FunSuite:

  override val munitTimeout = 5.minutes

  private val client = HttpClient.newHttpClient()

  /** Registers a source as a caller and answers with the status and the writer it was held with. */
  private def registerAs(id: String, headers: (String, String)*): (Int, String) =
    val builder = HttpRequest
      .newBuilder(URI.create(s"${ServiceFixture.baseUrl}/sources"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(s"""{"id":"$id","name":"a source"}"""))
    headers.foreach((name, value) => builder.header(name, value))
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    val writer = if response.statusCode == 201 then ujson.read(response.body)("writer").str else ""
    (response.statusCode, writer)

  private val gateway = LocalCallers.header(Caller.Gateway)

  private def bearer(token: String) = "Authorization" -> s"Bearer $token"

  test("a caller is named as a writer by its connection, or not at all") {
    assertEquals(Writers.of(Caller.Service("shop", "forecaster")), Some("service:shop/forecaster"))
    assertEquals(Writers.of(Caller.Machine("acme", "ci")), Some("machine:acme/ci"))
    assertEquals(Writers.of(Caller.Local), Some("local"))
    assertEquals(Writers.of(Caller.Gateway), None)
  }

  test("no caller is ever the writer a blueprint's run writes as") {
    assertEquals(Writers.blueprint("appraiser"), "blueprint:appraiser")
    val callers = Seq(
      Caller.Service("blueprint", "appraiser"),
      Caller.Machine("blueprint", "appraiser"),
      Caller.Local,
      Caller.Gateway
    )
    callers.flatMap(Writers.of).foreach(writer => assert(!writer.startsWith("blueprint:"), writer))
  }

  test("another service is the writer its certificate names, and two services are two writers") {
    val a = registerAs("writers-a", LocalCallers.header(Caller.Service("shop", "a")))
    val b = registerAs("writers-b", LocalCallers.header(Caller.Service("shop", "b")))
    assertEquals(a, (201, "service:shop/a"))
    assertEquals(b, (201, "service:shop/b"))
  }

  test("a registered machine is the writer the platform verified it as") {
    val machine = registerAs("writers-machine", LocalCallers.header(Caller.Machine("acme", "ci")))
    assertEquals(machine, (201, "machine:acme/ci"))
  }

  test("outside a cluster a caller with no certificate is local") {
    assertEquals(registerAs("writers-local"), (201, "local"))
  }

  test("a person through the gateway is the subject of their token at its issuer") {
    val (status, writer) =
      registerAs("writers-person", gateway, bearer(People.token("ada")))
    assertEquals((status, writer), (201, s"person:ada@${People.Name}"))
  }

  test("a caller through the gateway with no token, or one no listed issuer signed, is refused") {
    assertEquals(registerAs("writers-no-token", gateway)._1, 401)
    val stranger = People.token("ada", issuer = "https://elsewhere.example.test")
    assertEquals(registerAs("writers-stranger", gateway, bearer(stranger))._1, 401)
    assertEquals(registerAs("writers-garbage", gateway, bearer("not-a-token"))._1, 401)
    assertEquals(ServiceFixture.http.get("/sources/writers-no-token").status, 404)
    assertEquals(ServiceFixture.http.get("/sources/writers-stranger").status, 404)
  }

  test("a service that lists no issuer refuses every caller through the gateway") {
    assertEquals(Writers.people(Map.empty), None)
    intercept[IllegalStateException](Writers.people(Map("ANKKA_AUTH_ISSUERS" -> "people")))
  }

  test("a token does not change who a caller with a connection of its own is") {
    val (_, writer) = registerAs(
      "writers-service-token",
      LocalCallers.header(Caller.Service("shop", "c")),
      bearer(People.token("ada"))
    )
    assertEquals(writer, "service:shop/c")
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
