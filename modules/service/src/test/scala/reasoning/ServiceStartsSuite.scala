package reasoning

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.ProjectionRuntime
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import com.typesafe.config.ConfigFactory
import reasoning.belief.application.Clock
import reasoning.graph.GraphReader
import reasoning.service.{Providers, ReasoningService, Settings}

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
    assertEquals(settings.appraiserHolders, Vector("appraiser-a", "appraiser-b"))
    assertEquals(settings.goldenSet, None)
  }

  test("the service starts and is healthy with no model key and no judgment key") {
    val settings = Settings.from(ConfigFactory.load())
    val agents =
      ReasoningService.agents(settings, Providers.model(Map.empty), Providers.judgments(Map.empty))
    val server = HttpServer.at("127.0.0.1", 0)(
      ReasoningService.endpoints(
        settings,
        Clock.system,
        GraphReader.none,
        publishing = false,
        agents = Some(agents)
      )*
    )
    val kit =
      AnkkaTestKit.start(
        ReasoningService.components(settings, publishing = false),
        Seq(ProjectionRuntime(), agents, server)
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
      // A run of the appraiser starts, and fails at its first model call naming the missing key.
      val base = s"http://127.0.0.1:$port"
      val question = send(
        base,
        "/questions",
        Some(
          """{"id":"no-key","statement":"Is a run without a model key refused or failed?",
            |"hypotheses":[{"id":"yes","statement":"it fails"},{"id":"no","statement":"it runs"}]}""".stripMargin
        )
      )
      assertEquals(question.statusCode, 201, question.body)
      val started = send(
        base,
        "/process/runs",
        Some(
          """{"blueprint":"appraiser","runId":"no-key-may",
            |"input":{"tranche":"may","holder":"appraiser-a","question":"no-key"}}""".stripMargin
        )
      )
      assertEquals(started.statusCode, 201, started.body)
      val ended = reasoning.support.Eventually.eventually(2.minutes) {
        val run = send(base, "/process/runs/no-key-may", None)
        assertEquals(run.statusCode, 200, run.body)
        assert(run.body.contains("\"ending\""), s"the run has not ended: ${run.body}")
        run.body
      }
      println(s"a run with no model key: $ended")
      assert(ended.contains("\"status\":\"failed\""), ended)
      assert(ended.contains(Providers.ModelKey), ended)
    finally kit.stop()
  }

  private def send(base: String, path: String, body: Option[String]): HttpResponse[String] =
    val request = HttpRequest.newBuilder(URI.create(base + path))
    body.foreach(b => request.POST(HttpRequest.BodyPublishers.ofString(b)))
    HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString())
