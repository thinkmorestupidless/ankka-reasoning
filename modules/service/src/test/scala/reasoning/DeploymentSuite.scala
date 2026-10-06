package reasoning

import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.thinkmorestupidless.ankka.controlplane.api.{ServiceDescriptor, ServiceSpec}
import com.thinkmorestupidless.ankka.controlplane.api.Wire.given
import com.typesafe.config.ConfigFactory

import java.nio.file.{Files, Path}

/**
 * What is handed to a cluster is what a cluster accepts: the service descriptor decodes under
 * ankka's own rules with nothing to say against it, and the blueprint asks for the topic the
 * service publishes to, compacted within a day.
 */
class DeploymentSuite extends munit.FunSuite:

  /** The descriptor as `sbt deployDescriptors` renders it, with a cluster's values written in. */
  private def rendered: String =
    Files
      .readString(Path.of("deploy/service.json"))
      .replace("${IMAGE}", "reasoning:0.1.0")
      .replace("${ANKKA_VERSION}", "0.10.0")
      .replace("${KAFKA}", "kafka.kafka.svc:9092")
      .replace("${NEO4J_URI}", "bolt://neo4j.neo4j.svc:7687")

  test("deploy/service.json decodes as a service descriptor with no problems") {
    val text = rendered
    assert(!text.contains("${"), "a placeholder is left that the build does not render")
    val descriptor = readFromString[ServiceDescriptor](text)
    assertEquals(descriptor.name, "reasoning")
    assertEquals(descriptor.problems, Vector.empty)
    assertEquals(descriptor.service.hosting, ServiceSpec.Embedded)
    assertEquals(descriptor.service.resolvedPort, Some(9000))
  }

  test("the descriptor sets the service's own settings and none of the platform's") {
    val env = readFromString[ServiceDescriptor](rendered).service.env
    assertEquals(
      env.map(_.name),
      Vector(
        "ANKKA_KAFKA_BOOTSTRAP_SERVERS",
        "REASONING_NEO4J_URI",
        "REASONING_NEO4J_USERNAME",
        "REASONING_NEO4J_PASSWORD"
      )
    )
    // The graph database's credentials come from a secret and are never written in the descriptor.
    val secret = env.filter(e => e.name.endsWith("USERNAME") || e.name.endsWith("PASSWORD"))
    assert(
      secret.forall(e => e.value.isEmpty && e.secretKeyRef.exists(_.name == "reasoning-graph")),
      secret.toString
    )
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
    "the blueprint asks for the delta topic, compacted within a day, read from the start by the merge sink"
  ) {
    val blueprint = ConfigFactory
      .parseFile(Path.of("deploy/pipeline/blueprint.conf").toFile)
      .getConfig("blueprint")
    assertEquals(blueprint.getString("streamlets.graph"), "builtin/neo4j-merge-sink")
    val topic    = blueprint.getConfig("topics.reasoning-graph")
    val settings = ConfigFactory.load().getConfig("reasoning")
    assertEquals(topic.getString("topic.name"), settings.getString("graph-topic"))
    assertEquals(topic.getInt("partitions"), 3)
    assertEquals(topic.getStringList("consumers"), java.util.List.of("graph.in"))
    assertEquals(topic.getString("consumer-config.auto.offset.reset"), "earliest")
    assertEquals(topic.getLong("topic.max.compaction.lag.ms"), 86400000L)
  }
