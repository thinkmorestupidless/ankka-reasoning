package reasoning

import com.thinkmorestupidless.ankka.http.{Caller, LocalCallers}
import reasoning.belief.domain.Moment
import reasoning.seed.{Generate, Seed, Sent}
import reasoning.support.{GraphFixture, ServiceFixture, Walk}
import ujson.{Arr, Obj}

import java.nio.file.Paths
import java.time.Instant
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

/**
 * The seeded set, ten questions and more than five hundred records, recorded through the service
 * and read back from the graph and from the answers. What the scenarios show on one example, this
 * holds over all of it.
 */
class SeededSetSuite extends munit.FunSuite:

  override val munitTimeout = 30.minutes

  private val file    = Paths.get("seed/ten-questions.json")
  private val headers = Map(LocalCallers.header(Caller.Service("test", "seed")))
  private val http    = GraphFixture.http
  private val clock   = GraphFixture.clock

  private def rows[A](query: String, parameters: (String, Any)*)(
      row: reasoning.graph.Row => A
  ): Vector[A] =
    GraphFixture.graph.read(query, parameters.toMap)(row)

  /** What was sent, the records it made, and the node each is in the graph. */
  private final case class Seeded(
      sent: Vector[Sent],
      records: Vector[(String, String)],
      nodes: Vector[String],
      withdrawn: Vector[(String, String)]
  )

  private lazy val steps: Vector[ujson.Value] = Seed.steps(file)

  private lazy val seeded: Seeded =
    clock.set(ServiceFixture.Start)
    val records   = Vector.newBuilder[(String, String)]
    val nodes     = Vector.newBuilder[String]
    val withdrawn = Vector.newBuilder[(String, String)]
    val sent = Seed.send(
      steps,
      GraphFixture.baseUrl,
      headers,
      each = (step, answer) =>
        if !answer.refused then
          val path = step("post").str
          Seed.recordOf(path, step("body"), answer.json).foreach { (kind, id) =>
            records += kind                                        -> id
            if path.endsWith("/withdrawal") then withdrawn += kind -> id
            kind match
              case "question" =>
                nodes ++= s"question:$id" +: step("body")("hypotheses").arr
                  .map(h => s"hypothesis:$id/${h("id").str}")
              case "market" =>
                nodes ++= Seq(s"market:$id", s"holder:market.$id")
                if path.endsWith("/resolutions") then
                  nodes += s"resolution:$id/${step("body")("id").str}"
              case other => nodes += s"$other:$id"
          }
    )
    Seeded(sent, records.result().distinct, nodes.result().distinct, withdrawn.result())

  extension (sent: Sent) private def json: ujson.Value = ujson.read(sent.body)

  private def caughtUp(): Unit = seeded.records.foreach { (kind, id) =>
    val waited = http.post("/graph/wait", Obj("kind" -> kind, "id" -> id, "limitMs" -> 30000))
    assert(waited.json("caughtUp").bool, s"$kind $id: ${waited.body}")
  }

  /** The seeded set's part of the graph: each node, and each edge that leaves one. */
  private def graph(): (
      Vector[(String, Set[String], Map[String, Any])],
      Vector[(String, String, String, Map[String, Any])]
  ) =
    val nodes = rows(
      "MATCH (n:Element) WHERE n.id IN $ids RETURN n.id AS id, labels(n) AS labels, properties(n) AS properties",
      "ids" -> seeded.nodes
    ) { row =>
      (row.string("id"), row.strings("labels").toSet - "Element", row.properties("properties"))
    }
    val edges = rows(
      "MATCH (a:Element)-[r]->(b:Element) WHERE a.id IN $ids RETURN a.id AS from, type(r) AS type, b.id AS to, properties(r) AS properties",
      "ids" -> seeded.nodes
    )(row =>
      (row.string("from"), row.string("type"), row.string("to"), row.properties("properties"))
    )
    (nodes.sortBy(_._1), edges.sortBy(e => (e._1, e._2, e._3)))

  private val questions: Vector[String] = Vector(
    "bridge",
    "ferry",
    "harvest",
    "tram",
    "library",
    "reservoir",
    "festival",
    "orchard",
    "signal",
    "tunnel"
  )
  private val holders: Vector[String] = Vector("analyst-a", "analyst-b", "model-m")
  private val markets: Vector[String] = questions.take(Generate.Markets).map(q => s"$q-market")

  /** Every answer there is to ask of the set. */
  private val answers: Vector[String] =
    questions.flatMap { q =>
      Vector(
        s"/answers/case?hypothesis=$q/yes&stance=supports",
        s"/answers/case?hypothesis=$q/yes&stance=contradicts",
        s"/answers/case?hypothesis=$q/no&stance=supports",
        s"/answers/learned?question=$q&after=2026-03-20T00:00:00Z",
        s"/answers/resting-on-revised?question=$q",
        s"/answers/comparison?hypothesis=$q/yes&a=analyst-a&b=analyst-b",
        s"/answers/comparison?hypothesis=$q/yes&a=analyst-b&b=model-m"
      ) ++ holders.flatMap { h =>
        Vector(
          s"/answers/belief?holder=$h&hypothesis=$q/yes",
          s"/answers/belief-change?from=$h.$q.1&to=$h.$q.${Generate.RevisionsPerHolder}",
          s"/answers/belief-change?from=$h.$q.3&to=$h.$q.4"
        )
      }
    } ++ markets.map(m =>
      s"/answers/comparison?hypothesis=${m.stripSuffix("-market")}/yes&a=analyst-a&b=market.$m"
    ) ++
      Vector(
        s"/markets/${markets.head}/resolution?",
        s"/markets/${markets.head}/beliefs-at-resolution?"
      )

  /** Each answer's body. With `strict`, each has to be an answer. */
  private def ask(bound: String = "", strict: Boolean = true): Map[String, String] =
    answers.flatMap { path =>
      val answer = http.get(s"$path$bound")
      if strict then assertEquals(answer.status, 200, s"$path$bound: ${answer.body}")
      Option.when(answer.status == 200)(path -> answer.body)
    }.toMap

  private def differing(before: Map[String, String], after: Map[String, String]): Vector[String] =
    before.keys.toVector.sorted.filter(path => !after.get(path).contains(before(path)))

  test("the seeded set is recorded and reaches the graph") {
    assertEquals(
      seeded.sent.size,
      steps.size,
      s"refused at ${seeded.sent.last.path}: ${seeded.sent.last.body}"
    )
    assert(Generate.records(Generate.steps()) >= 500)
    assertEquals(
      ujson.read(ujson.write(Generate.steps())),
      ujson.read(ujson.write(steps)),
      "seed/ten-questions.json is not what its generator writes"
    )
    assertEquals(seeded.sent.count(_.created), Generate.records(Generate.steps()))
    assertEquals(questions.size, 10)
    caughtUp()
    val (nodes, edges) = graph()
    assertEquals(nodes.map(_._1), seeded.nodes.sorted)
    nodes.foreach((id, _, properties) =>
      assert(properties("_version").asInstanceOf[Long] >= 0, s"$id is a placeholder")
    )
    assert(edges.size > nodes.size)
  }

  test("every belief revision that rests on a claim reaches a source (SC-002)") {
    val resting = rows(
      "MATCH (r:BeliefRevision)-[:RESTS_ON]->(c:Claim) WHERE r.id IN $ids " +
        "RETURN r.id AS revision, c.id AS claim, EXISTS { (c)-[:DERIVES_FROM]->(:Evidence)-[:FROM_SOURCE]->(:Source) } AS sourced",
      "ids" -> seeded.nodes
    )(row => (row.string("revision"), row.string("claim"), row.boolean("sourced")))
    assertEquals(resting.map(_._1).distinct.size, 10 * 3 * Generate.RevisionsPerHolder)
    assertEquals(resting.filterNot(_._3), Vector.empty)
    // And the route from the service says the same of one of them, source and all.
    val belief = http.get("/answers/belief?holder=analyst-a&hypothesis=tram/yes").json
    assert(belief("restsOn").arr.nonEmpty)
    belief("restsOn").arr.foreach { support =>
      assert(support("evidence").arr.nonEmpty, ujson.write(support))
      support("evidence").arr.foreach(e => assert(e("source")("name").str.nonEmpty))
    }
  }

  test("every node and edge is of a kind the vocabulary names, with only its properties (FR-017)") {
    val layers = http.get("/graph/vocabulary").json("layers").arr
    def properties(kind: ujson.Value): (Set[String], Set[String]) =
      val all = kind("properties").arr
      (all.map(_("name").str).toSet, all.filterNot(_("optional").bool).map(_("name").str).toSet)
    val ofNode = layers.flatMap(_("nodes").arr.map(n => n("label").str -> properties(n))).toMap
    val ofEdge = layers
      .flatMap(
        _("edges").arr.map(e => e("type").str -> (e("from").str, e("to").str, properties(e)))
      )
      .toMap
    val sinkOwn = Set("id", "_version", "_deleted")
    val onEvery = Set("dated", "recordedAt")

    val (nodes, edges) = graph()
    val labelOf = nodes.map { (id, labels, own) =>
      assertEquals(labels.size, 1, s"$id has the labels $labels")
      val (named, needed) = ofNode.getOrElse(
        labels.head,
        fail(s"$id is a ${labels.head}, which the vocabulary does not name")
      )
      val has = own.keySet -- sinkOwn
      assertEquals(
        has -- onEvery -- named,
        Set.empty[String],
        s"$id has properties its kind does not name"
      )
      assertEquals(
        (needed ++ onEvery) -- has,
        Set.empty[String],
        s"$id lacks properties its kind requires"
      )
      assert(
        id.startsWith(s"${labels.head.toLowerCase.replace("beliefrevision", "revision")}:"),
        id
      )
      id -> labels.head
    }.toMap
    edges.foreach { (from, edgeType, to, own) =>
      val (fromKind, toKind, (named, needed)) = ofEdge.getOrElse(
        edgeType,
        fail(s"$from has an edge of type $edgeType, which the vocabulary does not name")
      )
      assertEquals(
        (labelOf(from), labelOf.get(to)),
        (fromKind, Some(toKind)),
        s"$from -[$edgeType]-> $to"
      )
      val has = own.keySet -- sinkOwn
      assertEquals(has -- named, Set.empty[String], s"$from -[$edgeType]-> $to")
      assertEquals(needed -- has, Set.empty[String], s"$from -[$edgeType]-> $to")
    }
    assertEquals(
      nodes.map(_._2.head).toSet,
      ofNode.keySet,
      "a kind of node the vocabulary names is not in the seeded set"
    )
    assertEquals(
      edges.map(_._2).toSet,
      ofEdge.keySet,
      "a kind of edge the vocabulary names is not in the seeded set"
    )
  }

  test(
    "no path returns to its start, and every link points to a record no later than its own (SC-004)"
  ) {
    val (nodes, edges) = graph()
    // Take away, again and again, every node nothing points to. What is left is on a cycle.
    val pointedAt = mutable.Map.from(nodes.map(_._1 -> 0))
    edges.foreach((_, _, to, _) => pointedAt(to) += 1)
    val leaving = edges.groupBy(_._1)
    val free    = mutable.Queue.from(pointedAt.collect { case (id, 0) => id })
    var removed = 0
    while free.nonEmpty do
      val id = free.dequeue()
      removed += 1
      leaving.getOrElse(id, Vector.empty).foreach { (_, _, to, _) =>
        pointedAt(to) -= 1
        if pointedAt(to) == 0 then free.enqueue(to)
      }
    assertEquals(removed, nodes.size, "some records are on a path that returns to its start")

    val kind = nodes.map((id, labels, _) => id -> labels.head).toMap
    val at = nodes
      .map((id, _, own) =>
        id -> (own("dated").asInstanceOf[Long], own("recordedAt").asInstanceOf[Long])
      )
      .toMap
    edges.foreach { (from, edgeType, to, _) =>
      assert(
        at(from)._2 >= at(to)._2,
        s"$from -[$edgeType]-> $to was recorded before what it links to"
      )
      // A holder and a source are dated when registered, which may be after what names them.
      if kind(to) != "Holder" && kind(to) != "Source" then
        assert(
          at(from)._1 >= at(to)._1,
          s"$from -[$edgeType]-> $to is dated before what it links to"
        )
    }
  }

  /** The text taken out of the two withdrawn records, as the seed file has it. */
  private lazy val withdrawnText: Vector[String] =
    val byName = steps.filter(_.obj.contains("as")).map(s => s("as").str -> s("body")).toMap
    steps.filter(_("post").str.endsWith("/withdrawal")).flatMap { step =>
      step("post").str.split('/').toList.drop(1) match
        case "evidence" :: reference :: _ =>
          val body = byName(reference.stripPrefix("{@").stripSuffix("}"))
          Vector(body("excerpt").str, body("locator").str)
        case "claims" :: id :: _ =>
          steps.collect {
            case s if s("post").str == "/claims" && s("body")("id").str == id =>
              s("body")("statement").str
          }
        case _ => Vector.empty
    }

  private def textInGraph(text: String): Vector[String] =
    rows(
      "MATCH (n:Element) WHERE any(k IN keys(n) WHERE toString(n[k]) CONTAINS $text) RETURN n.id AS id",
      "text" -> text
    )(_.string("id"))

  test(
    "withdrawn text is in no answer and not in the graph, and the records it was in still are (SC-011)"
  ) {
    caughtUp()
    assertEquals(withdrawnText.size, 3)
    assertEquals(seeded.withdrawn.map(_._1), Vector("evidence", "claim"))
    val all     = ask()
    val strings = all.values.toVector.flatMap(body => Walk.strings(ujson.read(body)))
    withdrawnText.foreach { text =>
      assert(!strings.exists(_.contains(text)), s"an answer holds the withdrawn text '$text'")
      assertEquals(textInGraph(text), Vector.empty, text)
    }
    // The records are still in the answers, marked, with their links.
    seeded.withdrawn.foreach((kind, id) =>
      assert(all.values.exists(_.contains(s"\"$id\"")), s"no answer names the withdrawn $kind $id")
    )
    val marked =
      all.values.toVector.flatMap(body => Walk.values(ujson.read(body), "withdrawn")).count(_.bool)
    assert(marked >= 2, s"only $marked records in the answers are marked withdrawn")
  }

  test("the whole set sent again leaves the records and the graph as they were (SC-003)") {
    caughtUp()
    val (nodes, edges) = graph()
    val written        = GraphFixture.topicEnd()
    val again          = Seed.send(steps, GraphFixture.baseUrl, headers)
    assertEquals(again.size, steps.size)
    again.foreach(answer => assertEquals(answer.status, 200, s"${answer.path}: ${answer.body}"))
    // What a repeat is answered with is the record held: as it now is, where it has been added to
    // or had its text withdrawn since it was first sent. A price observation also says whether it
    // added a revision, which the second time it did not.
    def held(answer: Sent): ujson.Value =
      val body = answer.json
      if body.obj.contains("withdrawal") then Obj("id" -> body("id"))
      else
        Seq("added", "resolutions").foreach(body.obj.remove(_): Unit)
        body
    def first(answer: Sent, now: ujson.Value): ujson.Value =
      if now.obj.keySet == Set("id") then Obj("id" -> answer.json("id")) else held(answer)
    val changed = again
      .zip(seeded.sent)
      .filter((second, once) => held(second) != first(once, held(second)))
      .map(_._1.path)
    assertEquals(changed, Vector.empty[String])
    Thread.sleep(
      8000
    ) // longer than a consumer waits between polls, so a delta would have been published
    assertEquals(GraphFixture.topicEnd(), written, "sending the set again published deltas")
    assertEquals(graph(), (nodes, edges))
  }

  test("every answer is the same from a graph database emptied and rebuilt (SC-005, SC-011)") {
    caughtUp()
    val before         = ask()
    val (nodes, edges) = graph()
    GraphFixture.stopSink()
    GraphFixture.emptyGraph()
    assertEquals(graph()._1, Vector.empty, "the graph database was not emptied")
    GraphFixture.startSink()
    caughtUp()
    val rebuilt = graph()
    // The sink's own bookkeeping apart, the rebuilt graph is the graph: same nodes, edges, properties.
    assertEquals(rebuilt, (nodes, edges))
    assertEquals(differing(before, ask()), Vector.empty[String])
    withdrawnText.foreach(text =>
      assertEquals(
        textInGraph(text),
        Vector.empty,
        s"the rebuilt graph holds the withdrawn text '$text'"
      )
    )
  }

  test("no answer as of a time holds a later record (SC-006)") {
    caughtUp()
    val sizes = Vector(
      "2026-03-12T00:00:00Z",
      "2026-03-25T12:00:00Z",
      "2026-04-10T00:00:00Z",
      "2026-05-20T00:00:00Z"
    ).map { asOf =>
      val bound = Instant.parse(asOf)
      val all   = ask(s"&asOf=$asOf", strict = false)
      val dates = all.toVector.flatMap((path, body) => Walk.dated(ujson.read(body)).map(path -> _))
      dates.foreach((path, dated) =>
        assert(!dated.isAfter(bound), s"$path as of $asOf holds a record dated $dated")
      )
      dates.size
    }
    assert(sizes.head > 50, s"as of the first time only ${sizes.head} dated records were seen")
    assertEquals(sizes, sizes.sorted, "a later time saw fewer records")
    assert(sizes.last > sizes.head)
  }

  test("no answer as recorded by a time changes after a back-dated record is entered (SC-006)") {
    caughtUp()
    val by = Moment(ServiceFixture.Start.millis + 86400000L).iso
    clock.set(Moment(ServiceFixture.Start.millis + 2 * 86400000L))
    try
      val before = ask(s"&asRecordedBy=$by")
      val open   = ask()
      // Entered two days after the set, about its first week: evidence, a claim, and a first
      // revision of a belief nobody had stated.
      val evidence = http.post(
        "/evidence",
        Obj(
          "source"     -> "council-minutes",
          "locator"    -> "https://records.example/council-minutes/bridge/late",
          "excerpt"    -> "A minute found late.",
          "observedAt" -> "2026-03-05T08:00:00Z"
        ),
        writer = "seed"
      )
      assertEquals(evidence.status, 201, evidence.body)
      val claim = http.post(
        "/claims",
        Obj(
          "id"          -> "bridge.found-late",
          "holder"      -> "analyst-a",
          "statement"   -> "the minute shows the works were agreed earlier than thought",
          "derivesFrom" -> Arr(evidence.json("id").str),
          "stances"     -> Arr(Obj("hypothesis" -> "bridge/yes", "stance" -> "supports")),
          "dated"       -> "2026-03-05T09:00:00Z"
        ),
        writer = "seed"
      )
      assertEquals(claim.status, 201, claim.body)
      val revision = http.post(
        "/beliefs/revisions",
        Obj(
          "id"          -> "reader-r.bridge.1",
          "holder"      -> "reader-r",
          "hypothesis"  -> "bridge/yes",
          "probability" -> 0.7,
          "restsOn"     -> Arr(Obj("claim" -> "bridge.found-late")),
          "dated"       -> "2026-03-06T12:00:00Z"
        ),
        writer = "seed"
      )
      assertEquals(revision.status, 201, revision.body)
      Seq("claim" -> "bridge.found-late", "revision" -> "reader-r.bridge.1").foreach { (kind, id) =>
        assert(
          http
            .post("/graph/wait", Obj("kind" -> kind, "id" -> id, "limitMs" -> 30000))
            .json("caughtUp")
            .bool,
          s"$kind $id"
        )
      }

      assertEquals(differing(before, ask(s"&asRecordedBy=$by")), Vector.empty[String])
      // Without the bound the same answers do change, so the bound is what held them.
      val changed = differing(open, ask())
      assert(
        changed.contains("/answers/case?hypothesis=bridge/yes&stance=supports"),
        changed.mkString(", ")
      )
    finally clock.set(ServiceFixture.Start)
  }
