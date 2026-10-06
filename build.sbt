// ankka-reasoning: a reasoning graph on ankka.
//
// Five modules. `graph` knows neither layer; `belief` names nothing of `market`; `market` builds on
// `belief`; `service` composes them and is the one deployable; `seed` is a client of the service
// and depends on none of them. The layering is the dependency direction, so a market word in the
// belief layer does not compile.

import Dependencies.*

ThisBuild / scalaVersion  := V.scala
ThisBuild / organization  := "com.thinkmorestupidless"
ThisBuild / versionScheme := Some("early-semver")
// No `ThisBuild / version`: sbt-dynver derives it from the nearest tag.

// Suites that start containers contend when they overlap. Do not undo it.
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

lazy val common = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all", "-Wvalue-discard"),
  javacOptions ++= Seq("--release", "21"),
  Test / fork              := true,
  Test / parallelExecution := false,
  Test / javaOptions ++= Seq("-Xmx2g"),
  libraryDependencies += munit % Test,
  publish / skip              := true
)

lazy val graph = project
  .in(file("modules/graph"))
  .settings(common)
  .settings(
    name := "reasoning-graph",
    libraryDependencies ++= Seq(ankkaSdk, neo4jDriver)
  )

lazy val belief = project
  .in(file("modules/belief"))
  .dependsOn(graph)
  .settings(common)
  .settings(
    name := "reasoning-belief",
    libraryDependencies ++= Seq(ankkaSdk, ankkaRuntime, ankkaHttp, ankkaTestkit % Test)
  )

lazy val market = project
  .in(file("modules/market"))
  .dependsOn(belief)
  .settings(common)
  .settings(
    name := "reasoning-market",
    libraryDependencies ++= Seq(ankkaSdk, ankkaRuntime, ankkaHttp, ankkaTestkit % Test)
  )

lazy val seed = project
  .in(file("modules/seed"))
  .settings(common)
  .settings(
    name := "reasoning-seed",
    libraryDependencies += ujson,
    run / fork          := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(belief, market, seed % Test)
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(common)
  .settings(
    name := "reasoning",
    libraryDependencies ++= Seq(
      ankkaSdk,
      ankkaRuntime,
      ankkaHttp,
      ankkaTestkit         % Test,
      ankkaControlPlaneApi % Test,
      ujson                % Test,
      kafkaClients         % Test,
      testcontainersKafka  % Test,
      testcontainersNeo4j  % Test,
      postgresql           % Test
    ),
    run / fork := true,
    // The living features are at the repository's root, and a forked test JVM starts where this says.
    Test / baseDirectory := (ThisBuild / baseDirectory).value,
    // A switch passed to sbt is set in a JVM that runs no tests; these are forwarded to the one
    // that does. A new `reasoning.*` test switch that is not listed here silently does nothing.
    Test / javaOptions ++= Seq(
      s"-Dreasoning.kafka.image=${sys.props.getOrElse("reasoning.kafka.image", V.kafkaImage)}",
      s"-Dreasoning.neo4j.image=${sys.props.getOrElse("reasoning.neo4j.image", V.neo4jImage)}",
      s"-Dreasoning.sink.image=${sys.props.getOrElse("reasoning.sink.image", V.sinkImage)}"
    ),
    Docker / packageName      := "reasoning",
    Docker / dockerRepository := sys.env.get("DOCKER_REPOSITORY"),
    Docker / version          := version.value.replace('+', '-'),
    dockerBaseImage           := "eclipse-temurin:21-jre",
    dockerUpdateLatest        := true,
    dockerExposedPorts        := Seq(9000)
  )

lazy val root = project
  .in(file("."))
  .aggregate(graph, belief, market, service, seed)
  .settings(name := "ankka-reasoning", publish / skip := true)

// The platform's database schema, taken out of the ankka-runtime artifact into target/ddl, where
// docker-compose.yml mounts it as Postgres' init directory. `sbt schema` after every ankka upgrade.
lazy val schema =
  taskKey[File]("Writes ankka's database schema from the runtime artifact into target/ddl")
schema := {
  val log = streams.value.log
  val jar = (service / Compile / dependencyClasspath).value
    .map(_.data)
    .find(f => f.getName.startsWith("ankka-runtime_") && f.getName.endsWith(".jar"))
    .getOrElse(sys.error("ankka-runtime is not on the classpath"))
  val out = (ThisBuild / baseDirectory).value / "target" / "ddl"
  IO.delete(out)
  IO.createDirectory(out)
  IO.unzip(jar, out, (entry: String) => entry.startsWith("ankka/ddl/") && entry.endsWith(".sql"))
  val files = (out / "ankka" / "ddl").listFiles().toList.sortBy(_.getName)
  files.foreach(f => IO.move(f, out / f.getName))
  IO.delete(out / "ankka")
  log.info(s"schema: ${files.map(_.getName).mkString(", ")} -> $out")
  out
}

// deploy/service.json with the image and the ankka version written in, into target/deploy. Where
// the broker and the graph database are is the cluster's to say: REASONING_DEPLOY_KAFKA and
// REASONING_DEPLOY_NEO4J_URI, and a placeholder left in is said so.
lazy val deployDescriptors = taskKey[File]("Renders deploy/service.json into target/deploy")
deployDescriptors := {
  val log   = streams.value.log
  val out   = (ThisBuild / baseDirectory).value / "target" / "deploy"
  val image = s"reasoning:${(service / Docker / version).value}"
  IO.createDirectory(out)
  val places = Seq(
    "${IMAGE}"         -> Some(image),
    "${ANKKA_VERSION}" -> Some(V.ankka),
    "${KAFKA}"         -> sys.env.get("REASONING_DEPLOY_KAFKA"),
    "${NEO4J_URI}"     -> sys.env.get("REASONING_DEPLOY_NEO4J_URI")
  )
  val rendered =
    places.foldLeft(IO.read((ThisBuild / baseDirectory).value / "deploy" / "service.json")) {
      case (text, (place, Some(value))) => text.replace(place, value)
      case (text, (place, None)) =>
        log.warn(s"deployDescriptors: $place is not set and is left in target/deploy/service.json")
        text
    }
  IO.write(out / "service.json", rendered)
  out
}
