package reasoning.support

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.ProjectionRuntime
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import reasoning.belief.application.SettableClock
import reasoning.graph.Neo4jGraph
import reasoning.service.ReasoningService

import scala.concurrent.duration.DurationInt

/**
 * The service with the process layer left out: its components not registered, its routes not
 * served, its vocabulary not named; the belief layer and the market layer whole. It publishes to
 * the graph fixture's topic and reads its graph database, through the same sink, so that every
 * scenario that reads the graph can run against it. What passes here passes without the process
 * layer.
 */
object NoProcessFixture:

  val clock: SettableClock = SettableClock(ServiceFixture.Start)

  private lazy val started: (AnkkaTestKit, HttpServer) =
    // Kafka, Neo4j, the topic and the sink are the graph fixture's.
    val _        = GraphFixture.kit
    val settings = GraphFixture.settings
    val reader   = Neo4jGraph(GraphFixture.connection, answerWithinMs = 2000L)
    val server = HttpServer.at("127.0.0.1", 0)(
      ReasoningService.endpoints(settings, clock, reader, publishing = true, process = false)*
    )
    val kit = AnkkaTestKit.start(
      ReasoningService.components(settings, publishing = true, process = false, clock = clock),
      Seq(ProjectionRuntime.withKafka(GraphFixture.kafka.getBootstrapServers), server),
      readyTimeout = 120.seconds
    )
    sys.addShutdownHook(kit.stop()): Unit
    (kit, server)

  lazy val http: Http =
    Http(
      s"http://127.0.0.1:${started._2.boundPort.getOrElse(sys.error("the server did not bind"))}"
    )
