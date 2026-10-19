package reasoning.support

import com.thinkmorestupidless.ankka.agent.{AgentRuntime, TestModelProvider}
import com.thinkmorestupidless.ankka.agent.judgment.TestJudgmentProvider
import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.{InMemoryBroker, ProjectionRuntime}
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import com.typesafe.config.ConfigFactory
import reasoning.belief.application.SettableClock
import reasoning.belief.domain.Moment
import reasoning.graph.GraphReader
import reasoning.service.{ReasoningService, Settings}

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt

/**
 * The whole service, with no Kafka and no graph database: one per test JVM, started the first time
 * a suite asks for it. Every suite that needs only the service shares it, and keeps out of the
 * others' way by naming its records after its own scenario.
 */
object ServiceFixture:

  /** After every date a scenario uses, so each of them is in the past. A step may set it. */
  val Start: Moment = Moment.parse("2026-12-01T00:00:00Z").get

  val clock: SettableClock = SettableClock(Start)

  /** A writer the service is told is a steward. */
  val Steward = "steward"

  val settings: Settings =
    Settings
      .from(ConfigFactory.load())
      .copy(
        stewards = Set(Http.writer(Steward)),
        people = Some(People.verifier),
        goldenSet = Some(Paths.get("modules/service/src/test/resources/golden-set"))
      )

  val broker: InMemoryBroker = InMemoryBroker()

  /**
   * The text model and the judgment every run in this JVM asks, the graph fixture's runs too. A
   * scenario scripts them; `FeatureSuite` resets both before each, so no script outlives its
   * scenario.
   */
  val model: TestModelProvider    = TestModelProvider()
  val judge: TestJudgmentProvider = TestJudgmentProvider()

  /** ankka's agents, hosting the appraiser; a step reads a run or a version through it. */
  lazy val agents: AgentRuntime = ReasoningService.agents(settings, model, judge, clock)

  private lazy val started: (AnkkaTestKit, HttpServer) =
    val server = HttpServer.at("127.0.0.1", 0)(
      ReasoningService.endpoints(
        settings,
        clock,
        GraphReader.none,
        publishing = true,
        agents = Some(agents)
      )*
    )
    val kit = AnkkaTestKit.start(
      ReasoningService.components(settings, publishing = true, clock = clock),
      Seq(ProjectionRuntime.withBroker(broker, broker), agents, server),
      readyTimeout = 120.seconds
    )
    sys.addShutdownHook(kit.stop()): Unit
    (kit, server)

  def kit: AnkkaTestKit = started._1

  lazy val baseUrl: String =
    s"http://127.0.0.1:${started._2.boundPort.getOrElse(sys.error("the server did not bind"))}"

  lazy val http: Http = Http(baseUrl)
