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
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / homepage := Some(url("https://reasoning.ankka.cloud/"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/thinkmorestupidless/ankka-reasoning"),
    "scm:git:https://github.com/thinkmorestupidless/ankka-reasoning.git"
  )
)
ThisBuild / developers := List(
  Developer(
    "thinkmorestupidless",
    "Trevor Burton-McCreadie",
    "",
    url("https://github.com/thinkmorestupidless")
  )
)
// No `ThisBuild / version`: sbt-dynver derives it from the nearest tag (`v0.1.0` is 0.1.0; a commit
// past it or a dirty tree is a -SNAPSHOT). Setting it anywhere silently overrides the tag, which is
// the one thing a release must not do.

// The local proof of the release path: `-Dreasoning.release.local=<dir>` points `publish` and
// `publishSigned` at a Maven-layout directory in place of the Central Portal, so the published
// modules, their sources, documentation and POMs can be looked at before anything is public.
ThisBuild / publishTo := sys.props
  .get("reasoning.release.local")
  .map(dir =>
    Resolver.file("local-release", file(dir))(Patterns(true, Resolver.mavenStyleBasePattern))
  )
  .orElse((ThisBuild / publishTo).value)

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

// The three layers are libraries as well as parts of the service: another ankka application can
// compose the belief layer, or both, into a service of its own. They are what a release publishes
// to Maven Central. The service is published as an image, and `seed` not at all.
lazy val published = Seq(publish / skip := false)

lazy val graph = project
  .in(file("modules/graph"))
  .settings(common)
  .settings(
    name := "ankka-reasoning-graph",
    description := "The vocabulary of a reasoning graph as a value, and the reader of the graph database.",
    libraryDependencies ++= Seq(ankkaSdk, neo4jDriver)
  )
  .settings(published)

lazy val belief = project
  .in(file("modules/belief"))
  .dependsOn(graph)
  .settings(common)
  .settings(
    name := "ankka-reasoning-belief",
    description := "The belief layer of ankka-reasoning: its records, rules, graph consumers, answers and routes.",
    libraryDependencies ++= Seq(ankkaSdk, ankkaRuntime, ankkaHttp, ankkaTestkit % Test)
  )
  .settings(published)

lazy val market = project
  .in(file("modules/market"))
  .dependsOn(belief)
  .settings(common)
  .settings(
    name        := "ankka-reasoning-market",
    description := "The market layer of ankka-reasoning, built on the belief layer.",
    libraryDependencies ++= Seq(ankkaSdk, ankkaRuntime, ankkaHttp, ankkaTestkit % Test)
  )
  .settings(published)

lazy val seed = project
  .in(file("modules/seed"))
  .settings(common)
  .settings(
    name := "ankka-reasoning-seed",
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
    // The image is named for the repository, since a registry is shared; the service it runs is
    // still `reasoning`.
    Docker / packageName      := "ankka-reasoning",
    Docker / dockerRepository := sys.env.get("DOCKER_REPOSITORY"),
    // A Docker tag may not contain '+', and a dynver snapshot version does.
    Docker / version := version.value.replace('+', '-'),
    dockerLabels ++= Map(
      // Names this repository, which is what links the package on ghcr.io to it.
      "org.opencontainers.image.source" -> "https://github.com/thinkmorestupidless/ankka-reasoning",
      "org.opencontainers.image.licenses" -> "Apache-2.0",
      "org.opencontainers.image.title"    -> "ankka-reasoning",
      "org.opencontainers.image.description" -> "The ankka-reasoning service: a reasoning graph on ankka."
    ),
    dockerBaseImage    := "eclipse-temurin:21-jre",
    dockerUpdateLatest := true,
    dockerExposedPorts := Seq(9000)
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
// REASONING_DEPLOY_NEO4J_URI, and a placeholder left in is said so. A release attaches the file,
// with those two left in, to its page.
lazy val deployDescriptors = taskKey[File]("Renders deploy/service.json into target/deploy")
deployDescriptors := {
  val log = streams.value.log
  val out = (ThisBuild / baseDirectory).value / "target" / "deploy"
  // The image as `service/Docker/publish` names it: with the registry in DOCKER_REPOSITORY, if any.
  val image = (service / Docker / dockerAlias).value.toString
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
