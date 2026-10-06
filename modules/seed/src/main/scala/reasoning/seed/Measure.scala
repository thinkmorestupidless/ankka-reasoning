package reasoning.seed

import ujson.{Arr, Obj}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}
import scala.jdk.CollectionConverters.*

/**
 * The two times the feature promises, measured against a running service with its graph beside it.
 *
 * The first is how long a record takes to reach the graph: from the service's answer to its write
 * to the graph holding its node and every edge it stated, for every record of the seeded set. The
 * second is how long an explanation takes on a question grown to a thousand records.
 *
 * Every run records under identifiers of its own, so it can be run again beside the last.
 */
object Measure:

  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  private def request(baseUrl: String, path: String, body: Option[String]): (Int, String) =
    val builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(90))
    body match
      case Some(json) =>
        builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json)): Unit
      case None => builder.GET(): Unit
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    (response.statusCode, response.body)

  private def waitFor(baseUrl: String, kind: String, id: String): Boolean =
    val (status, body) = request(
      baseUrl,
      "/graph/wait",
      Some(ujson.write(Obj("kind" -> kind, "id" -> id, "limitMs" -> 30000)))
    )
    status == 200 && ujson.read(body)("caughtUp").bool

  private def millis(since: Long): Long = (System.nanoTime() - since) / 1000000L

  /** The value below which `share` of the sorted values lie. */
  private def within(sorted: Vector[Long], share: Double): Long =
    sorted(((sorted.size - 1) * share).round.toInt)

  /** Every record of the seeded set, each timed from its `201` to its wait ending. */
  def timeToGraph(baseUrl: String, run: String): Vector[Long] =
    val times   = ConcurrentLinkedQueue[Long]()
    val missed  = ConcurrentLinkedQueue[String]()
    val waiters = Executors.newVirtualThreadPerTaskExecutor()
    val sent = Seed.send(
      Generate.steps(s"$run-"),
      baseUrl,
      each = (step, answer) =>
        if answer.created then
          val accepted = System.nanoTime()
          Seed.recordOf(step("post").str, step("body"), ujson.read(answer.body)).foreach {
            (kind, id) =>
              // The write is not held up by the wait: each is waited for on a thread of its own.
              waiters.execute { () =>
                if waitFor(baseUrl, kind, id) then times.add(millis(accepted)): Unit
                else missed.add(s"$kind $id"): Unit
              }
          }
    )
    sent.lastOption
      .filter(_.refused)
      .foreach(last => sys.error(s"the seeded set was refused at ${last.path}: ${last.body}"))
    waiters.shutdown()
    if !waiters.awaitTermination(5, TimeUnit.MINUTES) then sys.error("the waits did not end")
    if !missed.isEmpty then
      sys.error(
        s"${missed.size} records did not reach the graph within thirty seconds, the first ${missed.peek}"
      )
    times.asScala.toVector.sorted

  val GrownEvidence = 498

  /**
   * One question grown to a thousand records: 498 pieces of evidence, a claim from each, and two
   * revisions of one belief. The first rests on claims 1 to 200 and the second on claims 101 to
   * 498, so the explanation of the change holds every one of them.
   */
  def grow(baseUrl: String, run: String): (String, String) =
    val q             = s"$run-grown"
    val holder        = s"$run-grower"
    val source        = s"$run-ledger"
    def claim(i: Int) = s"$q.c$i"
    val evidence = (1 to GrownEvidence).toVector.flatMap { i =>
      Vector[ujson.Value](
        Obj(
          "post" -> "/evidence",
          "as"   -> s"e$i",
          "body" -> Obj(
            "source"  -> source,
            "locator" -> s"https://records.example/ledger/$run/$i",
            "excerpt" -> s"Ledger entry $i of run $run."
          )
        ),
        Obj(
          "post" -> "/claims",
          "body" -> Obj(
            "id"          -> claim(i),
            "holder"      -> holder,
            "statement"   -> s"entry $i bears on the question",
            "derivesFrom" -> Arr(s"@e$i"),
            "stances" -> Arr(
              Obj(
                "hypothesis" -> s"$q/yes",
                "stance"     -> (if i % 2 == 0 then "supports" else "contradicts")
              )
            )
          )
        )
      )
    }
    def revision(id: String, claims: Range, follows: Option[String]): ujson.Value =
      val body = Obj(
        "id"          -> id,
        "holder"      -> holder,
        "hypothesis"  -> s"$q/yes",
        "probability" -> (if follows.isEmpty then 0.4 else 0.7),
        "restsOn"     -> Arr.from(claims.map(i => Obj("claim" -> claim(i))))
      )
      follows.foreach(body("follows") = _)
      Obj("post" -> "/beliefs/revisions", "body" -> body)
    val (first, second) = (s"$q.r1", s"$q.r2")
    val steps = Vector[ujson.Value](
      Obj(
        "post" -> "/questions",
        "body" -> Obj(
          "id"        -> q,
          "statement" -> "Does a question with a thousand records still explain itself in time?",
          "hypotheses" -> Arr(
            Obj("id" -> "yes", "statement" -> "it does"),
            Obj("id" -> "no", "statement"  -> "it does not")
          )
        )
      ),
      Obj(
        "post" -> "/holders",
        "body" -> Obj("id" -> holder, "kind" -> "agent", "name" -> "the grower")
      ),
      Obj("post" -> "/sources", "body" -> Obj("id" -> source, "name" -> "the ledger"))
    ) ++ evidence ++ Vector(
      revision(first, 1 to 200, None),
      revision(second, 101 to GrownEvidence, Some(first))
    )
    val sent = Seed.send(steps, baseUrl)
    sent.lastOption
      .filter(_.refused)
      .foreach(last => sys.error(s"the grown question was refused at ${last.path}: ${last.body}"))
    // The last of each kind recorded, and both revisions: once these are in the graph the rest are.
    val last = ujson.read(sent(sent.size - 4).body)("id").str
    Seq(
      "revision" -> first,
      "revision" -> second,
      "claim"    -> claim(GrownEvidence),
      "evidence" -> last
    ).foreach { (kind, id) =>
      if !waitFor(baseUrl, kind, id) then sys.error(s"$kind $id did not reach the graph")
    }
    (first, second)

  /** The explanation of the change between the two revisions, asked `times` times. */
  def timeToAnswer(baseUrl: String, first: String, second: String, times: Int = 20): Vector[Long] =
    (1 to times).toVector.map { _ =>
      val started        = System.nanoTime()
      val (status, body) = request(baseUrl, s"/answers/belief-change?from=$first&to=$second", None)
      val took           = millis(started)
      if status != 200 then sys.error(s"the explanation was refused: $status $body")
      val answer = ujson.read(body)
      val held = answer("newlyRestedOn").arr.size + answer("noLongerRestedOn").arr.size + answer(
        "stillRestedOn"
      ).arr.size
      if held != GrownEvidence then
        sys.error(s"the explanation holds $held claims, not $GrownEvidence")
      took
    }.sorted

  def main(args: Array[String]): Unit =
    val baseUrl = if args.nonEmpty then args(0) else "http://localhost:9000"
    val run     = s"m${java.lang.Long.toString(System.currentTimeMillis() / 1000, 36)}"
    println(s"run $run against $baseUrl")

    val toGraph = timeToGraph(baseUrl, run)
    println(
      s"time to the graph over ${toGraph.size} records: median ${within(toGraph, 0.5)} ms, " +
        s"nine in ten within ${within(toGraph, 0.9)} ms, slowest ${toGraph.last} ms (target: five seconds, nine in ten)"
    )

    val (first, second) = grow(baseUrl, run)
    val toAnswer        = timeToAnswer(baseUrl, first, second)
    println(
      s"time to explain a belief's change over a question of ${2 * GrownEvidence + 6} records, asked ${toAnswer.size} times: " +
        s"median ${within(toAnswer, 0.5)} ms, slowest ${toAnswer.last} ms (target: one second)"
    )
    if within(toGraph, 0.9) > 5000L || toAnswer.last > 1000L then sys.exit(1)
