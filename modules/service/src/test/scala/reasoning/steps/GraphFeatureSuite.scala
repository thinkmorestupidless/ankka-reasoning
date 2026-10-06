package reasoning.steps

import reasoning.belief.application.SettableClock
import reasoning.support.{Eventually, GraphFixture, Http, LaunchExample}
import ujson.Obj

import scala.concurrent.duration.DurationInt

/**
 * A living feature that needs the graph: the service with Kafka, the sink and Neo4j beside it. The
 * steps here are those of `features/graph/`; the suites of the features that read the graph build
 * on them.
 */
abstract class GraphFeatureSuite(feature: String) extends FeatureSuite(feature):

  override val munitTimeout = 10.minutes

  override protected def http: Http           = GraphFixture.http
  override protected def clock: SettableClock = GraphFixture.clock

  /** The launch example as this scenario recorded it. */
  protected var example: LaunchExample = null

  /** Whether a step paused the graph database, so that the scenario's end can let it go. */
  private var paused = false

  override def afterEach(context: AfterEach): Unit =
    if paused then
      GraphFixture.unpauseGraph()
      paused = false

  // ── Helpers ───────────────────────────────────────────────────────────────

  protected def graphRows[A](query: String, parameters: (String, Any)*)(
      row: reasoning.graph.Row => A
  ): Vector[A] =
    GraphFixture.graph.read(query, parameters.toMap)(row)

  /** Waits until the graph holds a record's node and every edge it stated. */
  protected def caughtUp(kind: String, id: String): Unit =
    val answer = http.post("/graph/wait", Obj("kind" -> kind, "id" -> id, "limitMs" -> 30000))
    assertEquals(answer.status, 200, answer.body)
    assert(
      answer.json("caughtUp").bool,
      s"the graph did not catch up with $kind '$id': ${answer.body}\n${GraphFixture.sinkLogs}"
    )

  /** The records of the launch example, by kind. */
  protected def exampleRecords: Vector[(String, String)] = Vector(
    "question" -> example.question,
    "holder"   -> example.holder,
    "source"   -> example.filings,
    "source"   -> example.regulator,
    "evidence" -> example.filing,
    "evidence" -> example.notice,
    "claim"    -> example.pending,
    "claim"    -> example.barrierGone,
    "revision" -> example.first,
    "revision" -> example.second
  )

  /**
   * The node ids the launch example should have, with the label of each, as the vocabulary says.
   */
  protected def exampleNodes: Vector[(String, String)] = Vector(
    s"question:${example.question}" -> "Question",
    s"hypothesis:${example.yes}"    -> "Hypothesis",
    s"hypothesis:${example.no}"     -> "Hypothesis",
    s"holder:${example.holder}"     -> "Holder",
    s"source:${example.filings}"    -> "Source",
    s"source:${example.regulator}"  -> "Source",
    s"evidence:${example.filing}"   -> "Evidence",
    s"evidence:${example.notice}"   -> "Evidence",
    s"claim:${example.pending}"     -> "Claim",
    s"claim:${example.barrierGone}" -> "Claim",
    s"revision:${example.first}"    -> "BeliefRevision",
    s"revision:${example.second}"   -> "BeliefRevision"
  )

  /** The edges the launch example stated: type, from, to. */
  protected def exampleEdges: Vector[(String, String, String)] =
    val q   = s"question:${example.question}"
    val yes = s"hypothesis:${example.yes}"
    val no  = s"hypothesis:${example.no}"
    val h   = s"holder:${example.holder}"
    val c1  = s"claim:${example.pending}"
    val c2  = s"claim:${example.barrierGone}"
    val e1  = s"evidence:${example.filing}"
    val e2  = s"evidence:${example.notice}"
    val r1  = s"revision:${example.first}"
    val r2  = s"revision:${example.second}"
    Vector(
      ("ANSWERS", yes, q),
      ("ANSWERS", no, q),
      ("FROM_SOURCE", e1, s"source:${example.filings}"),
      ("FROM_SOURCE", e2, s"source:${example.regulator}"),
      ("STATED_BY", c1, h),
      ("STATED_BY", c2, h),
      ("DERIVES_FROM", c1, e1),
      ("DERIVES_FROM", c2, e2),
      ("CONTRADICTS", c1, yes),
      ("SUPPORTS", c2, yes),
      ("REVISES", c2, c1),
      ("HELD_BY", r1, h),
      ("HELD_BY", r2, h),
      ("BELIEF_IN", r1, yes),
      ("BELIEF_IN", r2, yes),
      ("RESTS_ON", r1, c1),
      ("RESTS_ON", r2, c2),
      ("FOLLOWS", r2, r1)
    )

  /** Every node of this scenario and every edge between them, as the graph has them now. */
  protected def snapshot(): (Set[String], Set[String]) =
    val ids = exampleNodes.map(_._1)
    val nodes = graphRows(
      "MATCH (n:Element) WHERE n.id IN $ids RETURN n.id AS id, labels(n) AS labels, properties(n) AS properties",
      "ids" -> ids
    ) { row =>
      s"${row.string("id")} ${row.strings("labels").sorted.mkString(",")} ${row.properties("properties").toVector.sortBy(_._1).mkString(",")}"
    }
    val edges = graphRows(
      "MATCH (a:Element)-[r]->(b:Element) WHERE a.id IN $ids RETURN a.id AS from, type(r) AS type, b.id AS to, properties(r) AS properties",
      "ids" -> ids
    ) { row =>
      s"${row.string("from")} ${row.string("type")} ${row.string("to")} ${row.properties("properties").toVector.sortBy(_._1).mkString(",")}"
    }
    (nodes.toSet, edges.toSet)

  /**
   * Writes a delta nobody else writes and waits for the sink to apply it: everything before it is
   * applied.
   */
  protected def sinkHasDrained(): Unit =
    val id = s"sentinel:${w.fresh("s")}"
    // Three partitions, and a key lands on one: a sentinel on each says each is drained.
    val sentinels = (0 until 12).map(i => s"$id-$i")
    GraphFixture.produce(sentinels.map { s =>
      s"node:$s".getBytes(
        "UTF-8"
      ) -> s"""{"kind":"node","id":"$s","version":1,"labels":["Sentinel"],"properties":{}}"""
        .getBytes("UTF-8")
    })
    Eventually.eventually(90.seconds) {
      val seen =
        graphRows("MATCH (n:Sentinel) WHERE n.id IN $ids RETURN n.id AS id", "ids" -> sentinels)(
          _.string("id")
        )
      assertEquals(seen.size, sentinels.size, GraphFixture.sinkLogs)
    }

  protected def recordExampleEvidence(): String =
    registerSource(): Unit
    recordEvidence()

  // ── The launch example in the graph ───────────────────────────────────────

  Given("the launch example") { () =>
    // Each day of the example is recorded on that day, so that what the service had been told by
    // a past time is a real question to ask of it. The clock is then put back.
    example = LaunchExample(http, w.p, Some(clock)).record()
    clock.set(clockStart)
    w.question = example.question
    w.hypothesis = example.yes
    w.hypotheses(Yes) = example.yes
    w.hypotheses(No) = example.no
    w.holder = example.holder
  }

  Given("the graph has caught up")(() => (exampleRecords ++ w.published).foreach(caughtUp(_, _)))

  Then(
    "the graph has a node for the question, each hypothesis, each holder, each source, each piece of evidence, each claim and each belief revision"
  ) { () =>
    val held = graphRows(
      "MATCH (n:Element) WHERE n.id IN $ids RETURN n.id AS id, labels(n) AS labels, n._version AS version",
      "ids" -> exampleNodes.map(_._1)
    ) { row =>
      (row.string("id"), row.strings("labels").toSet, row.long("version"))
    }
    assertEquals(held.map(_._1).toSet, exampleNodes.map(_._1).toSet)
    val labels = held.map(h => h._1 -> h._2).toMap
    exampleNodes.foreach((id, label) => assertEquals(labels(id), Set("Element", label), id))
    held.foreach(h => assert(h._3 >= 1, s"${h._1} is a placeholder"))
  }

  Then("the graph has an edge for each link a record stated") { () =>
    exampleEdges.foreach { (edgeType, from, to) =>
      val count = graphRows(
        "MATCH (a:Element {id: $from})-[r]->(b:Element {id: $to}) WHERE type(r) = $type RETURN count(r) AS n",
        "from" -> from,
        "to"   -> to,
        "type" -> edgeType
      )(_.long("n")).head
      assertEquals(count, 1L, s"$edgeType from $from to $to")
    }
    val all = graphRows(
      "MATCH (a:Element)-[r]->() WHERE a.id IN $ids RETURN count(r) AS n",
      "ids" -> exampleNodes.map(_._1)
    )(_.long("n")).head
    assertEquals(all, exampleEdges.size.toLong, "the graph has an edge no record stated")
  }

  When("a reader walks the edges from the node of the current belief revision") { () =>
    val walked = graphRows(
      "MATCH (r:BeliefRevision {id: $id})-[:RESTS_ON]->(c:Claim)-[:DERIVES_FROM]->(e:Evidence)-[:FROM_SOURCE]->(s:Source) " +
        "RETURN c.statement AS claim, e.id AS evidence, s.name AS source",
      "id" -> s"revision:${example.second}"
    )(row => (row.string("claim"), row.string("evidence"), row.string("source")))
    w.notes("walked") = walked.mkString("|")
    assertEquals(walked.size, 1, walked.toString)
    w.notes("claim") = walked.head._1
    w.notes("evidence") = walked.head._2
    w.notes("source") = walked.head._3
  }

  Then(
    "the reader reaches the claim {string}, the evidence it derives from and the source {string}"
  ) { (claim: String, source: String) =>
    assertEquals(w.notes("claim"), claim)
    assertEquals(w.notes("evidence"), s"evidence:${example.notice}")
    assertEquals(w.notes("source"), source)
  }

  When("a reader walks the edges from any node")(() => ())

  Then(
    "every edge runs from the node of the record that stated it to the node of a record held before it"
  ) { () =>
    val edges = graphRows(
      "MATCH (a:Element)-[r]->(b:Element) WHERE a.id IN $ids " +
        "RETURN a.id AS from, type(r) AS type, b.id AS to, a.recordedAt AS aRecorded, b.recordedAt AS bRecorded, a.dated AS aDated, b.dated AS bDated, labels(b) AS labels",
      "ids" -> exampleNodes.map(_._1)
    )(row =>
      (
        row.string("from"),
        row.string("type"),
        row.string("to"),
        row.long("aRecorded"),
        row.long("bRecorded"),
        row.long("aDated"),
        row.long("bDated"),
        row.strings("labels").toSet
      )
    )
    assertEquals(edges.size, exampleEdges.size)
    edges.foreach { (from, edgeType, to, aRecorded, bRecorded, aDated, bDated, labels) =>
      assert(bRecorded <= aRecorded, s"$edgeType from $from runs to $to, which was recorded later")
      // A holder and a source are registered, not stated: they are dated when they were
      // registered, and a record may be dated before that.
      if !labels.exists(Set("Holder", "Source")) then
        assert(bDated <= aDated, s"$edgeType from $from runs to $to, which is dated later")
    }
    assertEquals(edges.map(e => (e._2, e._1, e._3)).toSet, exampleEdges.toSet)
  }

  Then("the reader never returns to the node they started from") { () =>
    val loops = graphRows(
      "MATCH p = (a:Element)-[*1..12]->(a) WHERE a.id IN $ids RETURN count(p) AS n",
      "ids" -> exampleNodes.map(_._1)
    )(_.long("n")).head
    assertEquals(loops, 0L)
  }

  // ── Publishing again, and rebuilding ──────────────────────────────────────

  When("every record is published again") { () =>
    val (nodes, edges) = snapshot()
    w.notes("nodes") = nodes.toVector.sorted.mkString("\n")
    w.notes("edges") = edges.toVector.sorted.mkString("\n")
    val records = GraphFixture.topicRecords()
    assert(
      records.size >= exampleNodes.size + exampleEdges.size,
      s"the topic holds only ${records.size} records"
    )
    // The log is compacted, so what is published again is the latest of each element.
    GraphFixture.produce(records.map(r => r.key -> r.value))
    sinkHasDrained()
  }

  When("the graph database is emptied and rebuilt from the delta topic") { () =>
    val (nodes, edges) = snapshot()
    w.notes("nodes") = nodes.toVector.sorted.mkString("\n")
    w.notes("edges") = edges.toVector.sorted.mkString("\n")
    w.notes("topic") = GraphFixture.topicEnd().toString
    GraphFixture.stopSink()
    GraphFixture.emptyGraph()
    assertEquals(snapshot()._1, Set.empty[String], "the graph database was not emptied")
    GraphFixture.startSink()
    exampleRecords.foreach(caughtUp(_, _))
  }

  Then("the graph is as it was") { () =>
    val (nodes, edges) = snapshot()
    assertEquals(nodes.toVector.sorted.mkString("\n"), w.notes("nodes"))
    assertEquals(edges.toVector.sorted.mkString("\n"), w.notes("edges"))
  }

  Then("the service sent no record again") { () =>
    assertEquals(GraphFixture.topicEnd().toString, w.notes("topic"))
  }

  // ── Waiting, and a graph database that cannot be reached ──────────────────

  When("a writer records evidence and waits for the graph") { () =>
    val id = recordExampleEvidence()
    w.last = http.post("/graph/wait", Obj("kind" -> "evidence", "id" -> id, "limitMs" -> 30000))
  }

  Then(
    "the wait ends when the graph has a node for that evidence and an edge for each link it stated"
  ) { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assert(w.last.json("caughtUp").bool, w.last.body)
    assertEquals(w.last.json("missing").arr.size, 0)
    val node = graphRows(
      "MATCH (e:Evidence {id: $id}) RETURN e._version AS version",
      "id" -> s"evidence:${w.evidence}"
    )(_.long("version"))
    assertEquals(node, Vector(1L))
    val edge = graphRows(
      "MATCH (e:Evidence {id: $id})-[r:FROM_SOURCE]->(s:Source {id: $source}) RETURN r._version AS version",
      "id"     -> s"evidence:${w.evidence}",
      "source" -> s"source:${w.source}"
    )(_.long("version"))
    assertEquals(edge, Vector(1L))
  }

  Given("a graph database that cannot be reached") { () =>
    GraphFixture.pauseGraph()
    paused = true
  }

  When("a writer records evidence and waits for the graph with a limit") { () =>
    val id = recordExampleEvidence()
    w.last = http.post("/graph/wait", Obj("kind" -> "evidence", "id" -> id, "limitMs" -> 2000))
  }

  Then("the writer is told the graph does not yet hold the evidence") { () =>
    assertEquals(w.last.status, 200, w.last.body)
    assertEquals(w.last.json("caughtUp").bool, false)
    assert(
      w.last.json("missing").arr.map(_.str).contains(s"node:evidence:${w.evidence}"),
      w.last.body
    )
  }

  Then("the evidence is held") { () =>
    assertEquals(held(s"/evidence/${w.evidence}")("id").str, w.evidence)
  }

  Then("the graph has a node for it once the graph database is reached again") { () =>
    GraphFixture.unpauseGraph()
    paused = false
    Eventually.eventually(120.seconds) {
      val node = graphRows(
        "MATCH (e:Evidence {id: $id}) RETURN e.id AS id",
        "id" -> s"evidence:${w.evidence}"
      )(_.string("id"))
      assertEquals(node.size, 1, GraphFixture.sinkLogs)
    }
  }
