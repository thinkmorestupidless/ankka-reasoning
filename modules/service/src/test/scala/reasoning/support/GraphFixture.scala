package reasoning.support

import com.thinkmorestupidless.ankka.http.HttpServer
import com.thinkmorestupidless.ankka.runtime.ProjectionRuntime
import com.thinkmorestupidless.ankka.testkit.AnkkaTestKit
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import org.testcontainers.containers.{GenericContainer, Neo4jContainer, Network}
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.{DockerImageName, MountableFile}
import reasoning.belief.application.SettableClock
import reasoning.graph.{GraphConnection, GraphReader, Neo4jGraph}
import reasoning.service.{ReasoningService, Settings}

import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

/**
 * The service with everything a cluster would put beside it: Kafka with the delta topic created
 * compacted, Neo4j, and ankka-flow's released sidecar running the built-in merge sink. One per test
 * JVM, started the first time a suite asks for it.
 *
 * The sink is the image that will run and not a stand-in for it, because what matters here is what
 * the sink does: replacing a node's properties whole, placeholders, passing over a stale version.
 * Images are named by system properties the build forwards, never by a literal here.
 */
object GraphFixture:

  val Topic    = "reasoning-graph"
  val Password = "reasoning-test-password"

  private def image(property: String): DockerImageName =
    DockerImageName.parse(
      sys.props.getOrElse(property, sys.error(s"-D$property is not set; the build forwards it"))
    )

  private lazy val network: Network = Network.newNetwork()

  // An internal listener for the sink's container, and the mapped one for this JVM.
  lazy val kafka: KafkaContainer =
    val container = new KafkaContainer(image("reasoning.kafka.image"))
      .withNetwork(network)
      .withListener("kafka:19092")
    container.start()
    container

  // testcontainers' containers are typed by themselves, which Scala needs spelled out.
  private final class Neo4j(name: DockerImageName)   extends Neo4jContainer[Neo4j](name)
  private final class Sidecar(name: DockerImageName) extends GenericContainer[Sidecar](name)

  lazy val neo4j: Neo4jContainer[?] =
    val container = new Neo4j(image("reasoning.neo4j.image"))
      .withNetwork(network)
      .withNetworkAliases("neo4j")
      .withAdminPassword(Password)
    container.start()
    container

  lazy val connection: GraphConnection =
    GraphConnection(neo4j.getBoltUrl, "neo4j", Password, "neo4j")

  /** For a step that reads the graph database itself, as any reader of it could. */
  lazy val graph: GraphReader = Neo4jGraph(connection, answerWithinMs = 20000L)

  private def kafkaProperties: Properties =
    val properties = Properties()
    properties.put("bootstrap.servers", kafka.getBootstrapServers)
    properties

  /** The delta topic as the pipeline's operator would create it: compacted, three partitions. */
  private def createTopic(): Unit =
    val admin = AdminClient.create(kafkaProperties)
    try
      // Compacted, as the pipeline's operator creates it, with the clocks of compaction turned
      // down from a day to seconds so that a test can see a superseded record leave the log.
      val topic = new NewTopic(Topic, 3, 1.toShort).configs(
        Map(
          "cleanup.policy"            -> "compact",
          "max.compaction.lag.ms"     -> "3000",
          "min.compaction.lag.ms"     -> "0",
          "min.cleanable.dirty.ratio" -> "0.01",
          "segment.ms"                -> "2000",
          "delete.retention.ms"       -> "1000"
        ).asJava
      )
      admin.createTopics(java.util.List.of(topic)).all().get(): Unit
    finally admin.close()

  private val groups = AtomicInteger(0)

  @volatile private var sink: Option[GenericContainer[?]] = None

  /** The sink's files, as the operator would render and mount them. */
  private def sinkFiles(group: String): Path =
    val directory = Files.createTempDirectory("reasoning-sink")
    Files.writeString(
      directory.resolve("streamlet.conf"),
      s"""flow {
         |  pipeline  = reasoning-graph
         |  streamlet = graph
         |  config    = { secret = "neo4j-test", transaction-timeout = "10s" }
         |  stage {
         |    name = "neo4j-merge-sink"
         |    neo4j { credentials-dir = "/etc/flow/neo4j" }
         |  }
         |  inlets {
         |    in {
         |      topic             = "$Topic"
         |      group             = "$group"
         |      bootstrap.servers = "kafka:19092"
         |      consumer-config { auto.offset.reset = earliest }
         |      batch { max-records = 500, max-bytes = 1 MiB }
         |    }
         |  }
         |}
         |""".stripMargin
    ): Unit
    Files.copy(
      Path.of("compose/flow-graph/descriptor.json"),
      directory.resolve("descriptor.json")
    ): Unit
    Files.writeString(directory.resolve("uri"), "bolt://neo4j:7687"): Unit
    Files.writeString(directory.resolve("username"), "neo4j"): Unit
    Files.writeString(directory.resolve("password"), Password): Unit
    Files.writeString(directory.resolve("database"), "neo4j"): Unit
    directory

  /** Starts the sink under a consumer group of its own, so it reads the topic from the start. */
  def startSink(): Unit =
    val group              = s"reasoning-graph.graph.in.${groups.incrementAndGet()}"
    val files              = sinkFiles(group)
    def file(name: String) = MountableFile.forHostPath(files.resolve(name), 0x124) // 0444
    val container = new Sidecar(image("reasoning.sink.image"))
      .withNetwork(network)
      .withEnv("FLOW_CONFIG_DIR", "/etc/flow/config")
      .withCopyFileToContainer(file("streamlet.conf"), "/etc/flow/config/streamlet.conf")
      .withCopyFileToContainer(file("descriptor.json"), "/etc/flow/config/descriptor.json")
      .withCopyFileToContainer(file("uri"), "/etc/flow/neo4j/uri")
      .withCopyFileToContainer(file("username"), "/etc/flow/neo4j/username")
      .withCopyFileToContainer(file("password"), "/etc/flow/neo4j/password")
      .withCopyFileToContainer(file("database"), "/etc/flow/neo4j/database")
    container.start()
    sink = Some(container)

  def stopSink(): Unit =
    sink.foreach(_.stop())
    sink = None

  /** What the sink has logged, for a failure's message. */
  def sinkLogs: String = sink.fold("the sink is not running")(_.getLogs.takeRight(4000))

  /** Everything out of the graph database. The sink's constraint stays. */
  def emptyGraph(): Unit =
    val session = org.neo4j.driver.GraphDatabase
      .driver(neo4j.getBoltUrl, org.neo4j.driver.AuthTokens.basic("neo4j", Password))
    try
      val s = session.session()
      try s.run("MATCH (n) DETACH DELETE n").consume(): Unit
      finally s.close()
    finally session.close()

  def pauseGraph(): Unit =
    neo4j.getDockerClient.pauseContainerCmd(neo4j.getContainerId).exec(): Unit
  def unpauseGraph(): Unit =
    neo4j.getDockerClient.unpauseContainerCmd(neo4j.getContainerId).exec(): Unit

  /** Every record on the topic, from the start, as it stands when asked. */
  def topicRecords(): Vector[ConsumerRecord[Array[Byte], Array[Byte]]] =
    val properties = kafkaProperties
    properties.put("group.id", s"reader-${groups.incrementAndGet()}")
    properties.put("enable.auto.commit", "false")
    val consumer =
      new KafkaConsumer(properties, new ByteArrayDeserializer, new ByteArrayDeserializer)
    try
      val partitions = consumer
        .partitionsFor(Topic)
        .asScala
        .map(p => new TopicPartition(Topic, p.partition))
        .asJava
      consumer.assign(partitions)
      consumer.seekToBeginning(partitions)
      val ends     = consumer.endOffsets(partitions).asScala
      val records  = Vector.newBuilder[ConsumerRecord[Array[Byte], Array[Byte]]]
      var done     = ends.forall((_, end) => end == 0L)
      val deadline = System.nanoTime() + 30.seconds.toNanos
      while !done && System.nanoTime() < deadline do
        consumer.poll(Duration.ofMillis(500)).asScala.foreach(records += _)
        done = ends.forall((partition, end) => consumer.position(partition) >= end)
      records.result()
    finally consumer.close()

  /**
   * How many records have ever been written to the topic: the sum of its partitions' end offsets.
   */
  def topicEnd(): Long =
    val properties = kafkaProperties
    properties.put("group.id", s"end-${groups.incrementAndGet()}")
    val consumer =
      new KafkaConsumer(properties, new ByteArrayDeserializer, new ByteArrayDeserializer)
    try
      val partitions = consumer
        .partitionsFor(Topic)
        .asScala
        .map(p => new TopicPartition(Topic, p.partition))
        .asJava
      consumer.endOffsets(partitions).asScala.values.map(_.longValue).sum
    finally consumer.close()

  /** Produces records to the topic as given: for a hand-written delta, and for publishing again. */
  def produce(records: Seq[(Array[Byte], Array[Byte])]): Unit =
    val producer =
      new KafkaProducer(kafkaProperties, new ByteArraySerializer, new ByteArraySerializer)
    try
      records.foreach((key, value) =>
        producer.send(new ProducerRecord(Topic, key, value)).get(): Unit
      )
      producer.flush()
    finally producer.close()

  // ── The service ───────────────────────────────────────────────────────────

  val clock: SettableClock = SettableClock(ServiceFixture.Start)

  lazy val settings: Settings =
    Settings
      .from(ConfigFactory.load())
      .copy(graph = Some(connection), stewards = Set(Http.writer(ServiceFixture.Steward)))

  private lazy val started: (AnkkaTestKit, HttpServer) =
    createTopic()
    val _ = neo4j
    startSink()
    // The service gives up on a read sooner than a person would: a database that has stopped
    // answering costs a reader two seconds.
    val reader = Neo4jGraph(connection, answerWithinMs = 2000L)
    val server = HttpServer.at("127.0.0.1", 0)(
      ReasoningService.endpoints(settings, clock, reader, publishing = true)*
    )
    val kit = AnkkaTestKit.start(
      ReasoningService.components(settings, publishing = true),
      Seq(ProjectionRuntime.withKafka(kafka.getBootstrapServers), server),
      readyTimeout = 120.seconds
    )
    sys.addShutdownHook {
      kit.stop()
      stopSink()
      neo4j.stop()
      kafka.stop()
    }: Unit
    (kit, server)

  def kit: AnkkaTestKit = started._1

  lazy val baseUrl: String =
    s"http://127.0.0.1:${started._2.boundPort.getOrElse(sys.error("the server did not bind"))}"

  lazy val http: Http = Http(baseUrl)
