package reasoning.support

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.{InMemoryBroker, ProjectionRuntime}
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import reasoning.belief.application.SettableClock
import reasoning.graph.GraphReader
import reasoning.service.ReasoningService

import scala.concurrent.duration.DurationInt

/**
 * The service with the market layer left out: its components not registered, its routes not served,
 * its holder kind not named. What passes here passes on the belief layer alone.
 */
object BeliefOnlyFixture:

  val clock: SettableClock = SettableClock(ServiceFixture.Start)

  private val broker = InMemoryBroker()

  private lazy val started: (AnkkaTestKit, HttpServer) =
    val settings = ServiceFixture.settings
    val server = HttpServer.at("127.0.0.1", 0)(
      ReasoningService.endpoints(
        settings,
        clock,
        GraphReader.none,
        publishing = true,
        markets = false
      )*
    )
    val kit = AnkkaTestKit.start(
      ReasoningService.components(settings, publishing = true, markets = false),
      Seq(ProjectionRuntime.withBroker(broker, broker), server),
      readyTimeout = 120.seconds
    )
    sys.addShutdownHook(kit.stop()): Unit
    (kit, server)

  lazy val http: Http =
    Http(
      s"http://127.0.0.1:${started._2.boundPort.getOrElse(sys.error("the server did not bind"))}"
    )
