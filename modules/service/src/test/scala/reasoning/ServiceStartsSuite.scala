package reasoning

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.ProjectionRuntime
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import com.typesafe.config.ConfigFactory
import reasoning.belief.application.Clock
import reasoning.graph.GraphReader
import reasoning.service.{ReasoningService, Settings}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.concurrent.duration.DurationInt

/** The service starts under the test kit and answers its health route. */
class ServiceStartsSuite extends munit.FunSuite:

  override val munitTimeout = 3.minutes

  test("the settings are read from application.conf with their defaults") {
    val settings = Settings.from(ConfigFactory.load())
    assertEquals(settings.graphTopic, "reasoning-graph")
    assertEquals(settings.graph, None)
    assertEquals(settings.excerptLimit, 2000)
    assertEquals(settings.waitLimitMaxMs, 30000L)
    assertEquals(settings.stewards, Set.empty[String])
  }

  test("the service starts and is healthy") {
    val settings = Settings.from(ConfigFactory.load())
    val server = HttpServer.at("127.0.0.1", 0)(
      ReasoningService.endpoints(settings, Clock.system, GraphReader.none, publishing = false)*
    )
    val kit =
      AnkkaTestKit.start(
        ReasoningService.components(settings, publishing = false),
        Seq(ProjectionRuntime(), server)
      )
    try
      val port = server.boundPort.getOrElse(fail("the server did not bind"))
      val response = HttpClient
        .newHttpClient()
        .send(
          HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/_ankka/health")).build(),
          HttpResponse.BodyHandlers.ofString()
        )
      assertEquals((response.statusCode, response.body), (200, "ok"))
    finally kit.stop()
  }
