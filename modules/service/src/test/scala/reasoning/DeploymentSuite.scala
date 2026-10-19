package reasoning

import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.thinkmorestupidless.ankka.controlplane.api.{ServiceDescriptor, ServiceSpec}
import com.thinkmorestupidless.ankka.controlplane.api.Wire.given
import com.typesafe.config.ConfigFactory

import java.nio.file.{Files, Path}

/**
 * What is handed to a cluster is what a cluster accepts: both descriptors decode under ankka's own
 * rules with nothing to say against them, and the sink reads the topic the service publishes to.
 */
class DeploymentSuite extends munit.FunSuite:

  /** A descriptor as `sbt deployDescriptors` renders it, with a cluster's values written in. */
  private def rendered(name: String = "service.json"): String =
    Files
      .readString(Path.of(s"deploy/$name"))
      .replace("${IMAGE}", "ghcr.io/thinkmorestupidless/ankka-reasoning:0.1.0")
      .replace("${SINK_IMAGE}", "ghcr.io/thinkmorestupidless/ankka-graph-sink-neo4j:0.1.0")
      .replace("${ANKKA_VERSION}", "0.12.0")
      .replace("${KAFKA}", "kafka.kafka.svc:9092")
      .replace("${NEO4J_URI}", "bolt://neo4j.neo4j.svc:7687")

  private def sink = readFromString[ServiceDescriptor](rendered("graph-sink.json"))

  /** The graph database's credentials come from a secret and are never written in a descriptor. */
  private def credentialsFromSecret(
      env: Vector[com.thinkmorestupidless.ankka.controlplane.api.EnvVar]
  ) =
    val secret = env.filter(e => e.name.endsWith("USERNAME") || e.name.endsWith("PASSWORD"))
    assertEquals(secret.size, 2, env.toString)
    assert(
      secret.forall(e => e.value.isEmpty && e.secretKeyRef.exists(_.name == "reasoning-graph")),
      secret.toString
    )

  test("deploy/service.json decodes as a service descriptor with no problems") {
    val text = rendered()
    assert(!text.contains("${"), "a placeholder is left that the build does not render")
    val descriptor = readFromString[ServiceDescriptor](text)
    assertEquals(descriptor.name, "reasoning")
    assertEquals(descriptor.problems, Vector.empty)
    assertEquals(descriptor.service.hosting, ServiceSpec.Embedded)
    assertEquals(descriptor.service.resolvedPort, Some(9000))
  }

  test("the descriptor sets the service's own settings and none of the platform's") {
    val env = readFromString[ServiceDescriptor](rendered()).service.env
    assertEquals(
      env.map(_.name),
      Vector(
        "ANKKA_KAFKA_BOOTSTRAP_SERVERS",
        "REASONING_NEO4J_URI",
        "REASONING_NEO4J_USERNAME",
        "REASONING_NEO4J_PASSWORD"
      )
    )
    credentialsFromSecret(env)
    // Every REASONING_ variable it sets is one application.conf reads.
    val read = Files.readString(Path.of("modules/service/src/main/resources/application.conf"))
    env
      .map(_.name)
      .filter(_.startsWith("REASONING_"))
      .foreach(name =>
        assert(read.contains(s"$${?$name}"), s"application.conf does not read $name")
      )
  }

  test(
    "deploy/graph-sink.json decodes as a service descriptor with no problems, and keeps no state"
  ) {
    val text = rendered("graph-sink.json")
    assert(!text.contains("${"), "a placeholder is left that the build does not render")
    assertEquals(sink.name, "reasoning-graph-sink")
    assertEquals(sink.problems, Vector.empty)
    assert(text.contains("\"database\": \"none\""), text)
    assert(text.contains("\"http\": false"), text)
  }

  test("the sink reads the topic the service publishes to, into the graph database it reads") {
    val env = sink.service.env
    assertEquals(
      env.map(_.name),
      Vector(
        "ANKKA_GRAPH_SINK_TOPIC",
        "ANKKA_GRAPH_SINK_VERSION",
        "NEO4J_URI",
        "NEO4J_USERNAME",
        "NEO4J_PASSWORD",
        "NEO4J_DATABASE"
      )
    )
    val settings = ConfigFactory.load().getConfig("reasoning")
    assertEquals(
      env.find(_.name == "ANKKA_GRAPH_SINK_TOPIC").flatMap(_.value),
      Some(settings.getString("graph-topic"))
    )
    credentialsFromSecret(env)
  }
