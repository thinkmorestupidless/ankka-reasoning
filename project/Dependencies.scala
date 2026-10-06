import sbt.*

/** Single source of truth for every external version. */
object Dependencies {

  object V {
    val scala          = "3.9.0"
    val ankka          = "0.10.0"
    val neo4jDriver    = "5.28.5"
    val kafkaClients   = "3.9.2"
    val ujson          = "4.4.3"
    val munit          = "1.3.6"
    val testcontainers = "1.21.4"
    val postgresql     = "42.7.13"

    // Images the tests and the compose file run. A test never names one by a literal.
    val kafkaImage = "apache/kafka:3.9.1"
    val neo4jImage = "neo4j:5.26-community"
    val sinkImage  = "ghcr.io/thinkmorestupidless/ankka-flow-sidecar:0.3.0"
  }

  private def ankka(module: String) = "com.thinkmorestupidless" %% s"ankka-$module" % V.ankka

  val ankkaSdk             = ankka("sdk")
  val ankkaRuntime         = ankka("runtime")
  val ankkaHttp            = ankka("http")
  val ankkaTestkit         = ankka("testkit")
  val ankkaControlPlaneApi = ankka("controlplane-api")

  val neo4jDriver = "org.neo4j.driver" % "neo4j-java-driver" % V.neo4jDriver

  // For the seeding client and for reading replies in tests: neither is the service's own JSON.
  val ujson = "com.lihaoyi" %% "ujson" % V.ujson

  val munit               = "org.scalameta"     %% "munit"         % V.munit
  val kafkaClients        = "org.apache.kafka"   % "kafka-clients" % V.kafkaClients
  val testcontainersKafka = "org.testcontainers" % "kafka"         % V.testcontainers
  val testcontainersNeo4j = "org.testcontainers" % "neo4j"         % V.testcontainers

  // For the one suite that searches the service's database, row by row, for text that was withdrawn.
  val postgresql = "org.postgresql" % "postgresql" % V.postgresql
}
