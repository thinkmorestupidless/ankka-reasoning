package reasoning.seed

import ujson.{Arr, Obj}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.nio.file.{Files, Paths}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, Executors, TimeUnit}
import scala.concurrent.duration.DurationInt
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

  private[seed] def request(baseUrl: String, path: String, body: Option[String]): (Int, String) =
    val builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(90))
    body match
      case Some(json) =>
        builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json)): Unit
      case None => builder.GET(): Unit
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    (response.statusCode, response.body)

  private[seed] def waitFor(baseUrl: String, kind: String, id: String): Boolean =
    val (status, body) = request(
      baseUrl,
      "/graph/wait",
      Some(ujson.write(Obj("kind" -> kind, "id" -> id, "limitMs" -> 30000)))
    )
    status == 200 && ujson.read(body)("caughtUp").bool

  private[seed] def millis(since: Long): Long = (System.nanoTime() - since) / 1000000L

  /** The value below which `share` of the sorted values lie. */
  def within(sorted: Vector[Long], share: Double): Long =
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

/**
 * The golden set's four runs of the appraiser, timed against a running service with its graph
 * beside it and a model behind it: each record a run wrote, to the graph with its act, from the
 * run's end; and the whole set, from the first run started to the comparison of the two holders
 * answered from the graph with every act in it.
 *
 * A record's time is counted from the moment its run is seen to have ended, not from its write,
 * which a client over HTTP cannot see; so each figure is an upper bound on the time from the write.
 * Every run records under a question of its own, so it can be run again beside the last.
 */
object MeasureGoldenSet:

  val Holders: Vector[String]  = Vector("appraiser-a", "appraiser-b")
  val Tranches: Vector[String] = Vector("may", "june")

  /** The last day of a tranche's month, after everything in it is published. */
  def observedAt(tranche: String): String =
    if tranche == "june" then "2026-06-30T09:00:00Z" else "2026-05-31T09:00:00Z"

  final case class Timed(record: String, act: String, millis: Long)

  final case class Result(
      question: String,
      runs: Vector[String],
      runMillis: Vector[Long],
      records: Vector[Timed],
      wholeMillis: Long
  ):
    def recordTimes: Vector[Long] = records.map(_.millis).sorted

  private def get(baseUrl: String, path: String): ujson.Value =
    val (status, body) = Measure.request(baseUrl, path, None)
    if status != 200 then sys.error(s"GET $path answered $status: $body")
    ujson.read(body)

  private def post(baseUrl: String, path: String, body: ujson.Value): ujson.Value =
    val (status, answer) = Measure.request(baseUrl, path, Some(ujson.write(body)))
    if status != 200 && status != 201 then sys.error(s"POST $path answered $status: $answer")
    ujson.read(answer)

  /**
   * The golden set's question under the run's prefix; the sources the service registered itself.
   */
  def seedQuestion(baseUrl: String, run: String): String =
    val question = ujson.read(Files.readString(Paths.get("golden-set", "question.json")))
    val id       = s"$run-${question("id").str}"
    post(
      baseUrl,
      "/questions",
      Obj(
        "id"         -> id,
        "statement"  -> question("statement"),
        "hypotheses" -> question("hypotheses"),
        "dated"      -> "2026-05-01T09:00:00Z"
      )
    ): Unit
    id

  /** Starts one run and waits for its ending; the moment it was seen to have ended, in nanos. */
  def runTranche(
      baseUrl: String,
      question: String,
      holder: String,
      tranche: String,
      id: String,
      follows: Option[String]
  ): Long =
    val input = Obj(
      "tranche"    -> tranche,
      "holder"     -> holder,
      "question"   -> question,
      "observedAt" -> observedAt(tranche)
    )
    follows.foreach(f => input("follows") = f)
    post(
      baseUrl,
      "/process/runs",
      Obj("blueprint" -> "appraiser", "runId" -> id, "input" -> input)
    ): Unit
    val deadline = System.nanoTime() + 15.minutes.toNanos
    var ended    = Option.empty[ujson.Value]
    while ended.isEmpty && System.nanoTime() < deadline do
      val run = get(baseUrl, s"/process/runs/$id")
      ended = run.obj.get("ending")
      if ended.isEmpty then Thread.sleep(200)
    val ending = ended.getOrElse(sys.error(s"the run $id did not end in time"))
    if ending("status").str != "completed" then
      sys.error(
        s"the run $id ended ${ending("status").str}: ${ending.obj.get("reason").fold("")(_.str)}"
      )
    System.nanoTime()

  /**
   * Every record the run wrote, timed from `since` to the graph holding the record and its act: the
   * record through `/graph/wait`, the act through the run's answer from the graph.
   */
  def timeRecords(baseUrl: String, run: String, tranche: String, since: Long): Vector[Timed] =
    val acts = actsOf(baseUrl, run, tranche)
    val seen = ConcurrentHashMap[String, Long]()
    // One poller for the acts of the run in the graph; one waiter per record.
    val poller = Thread.ofVirtual().start { () =>
      val deadline = System.nanoTime() + 2.minutes.toNanos
      def missing  = acts.exists(a => !seen.containsKey(a("id").str))
      while missing && System.nanoTime() < deadline do
        val (status, body) = Measure.request(baseUrl, s"/process/answers/run?run=$run", None)
        if status == 200 then
          ujson.read(body)("wrote").arr.foreach { w =>
            seen.putIfAbsent(w("act")("id").str, System.nanoTime()): Unit
          }
        if missing then Thread.sleep(100)
    }
    val waiters = Executors.newVirtualThreadPerTaskExecutor()
    val timed = acts.map { act =>
      val kind = act("record")("kind").str
      val id   = act("record")("id").str
      waiters.submit[Timed] { () =>
        if !Measure.waitFor(baseUrl, kind, id) then
          sys.error(s"$kind $id of the run $run did not reach the graph within thirty seconds")
        val recordAt = System.nanoTime()
        poller.join()
        val actAt = Option(seen.get(act("id").str))
          .getOrElse(sys.error(s"the act ${act("id").str} of the run $run did not reach the graph"))
        Timed(s"$kind:$id", act("id").str, (recordAt.max(actAt) - since) / 1000000L)
      }
    }
    waiters.shutdown()
    timed.map(_.get())

  /**
   * The acts of a run once the rows they are listed from have caught up with its writes: a claim
   * per document of the tranche and one revision, which every run writes, and whatever evidence the
   * run recorded first; the list is taken once it has stood still for a moment.
   */
  def actsOf(baseUrl: String, run: String, tranche: String): Vector[ujson.Value] =
    val documents =
      ujson.read(Files.readString(Paths.get("golden-set", s"$tranche.json")))("documents").arr.size
    val deadline = System.nanoTime() + 2.minutes.toNanos
    var last     = Vector.empty[ujson.Value]
    var still    = 0
    while still < 5 && System.nanoTime() < deadline do
      val acts   = get(baseUrl, s"/process/runs/$run/acts")("acts").arr.toVector
      val kinds  = acts.groupBy(_("record")("kind").str).map((k, v) => k -> v.size)
      val enough = kinds.getOrElse("claim", 0) == documents && kinds.getOrElse("revision", 0) == 1
      if enough && acts.size == last.size then still += 1 else still = 0
      last = acts
      if still < 5 then Thread.sleep(200)
    if still < 5 then sys.error(s"the acts of the run $run were not listed in time")
    last

  def measure(baseUrl: String, run: String, log: String => Unit = println): Result =
    val question = seedQuestion(baseUrl, run)
    val started  = System.nanoTime()
    val runs     = Vector.newBuilder[String]
    val runTimes = Vector.newBuilder[Long]
    val records  = Vector.newBuilder[Timed]
    Holders.foreach { holder =>
      Tranches.foreach { tranche =>
        val id      = s"$run-$holder-$tranche"
        val follows = Option.when(tranche != Tranches.head)(s"$run-$holder-${Tranches.head}.yes")
        val from    = System.nanoTime()
        val ended   = runTranche(baseUrl, question, holder, tranche, id, follows)
        runs += id
        runTimes += (ended - from) / 1000000L
        log(s"run $id ended after ${(ended - from) / 1000000L} ms")
        records ++= timeRecords(baseUrl, id, tranche, ended)
      }
    }
    // The whole set is in the graph when the two holders compare, with every act in the answer.
    val deadline = System.nanoTime() + 2.minutes.toNanos
    var answered = false
    while !answered && System.nanoTime() < deadline do
      val (status, body) = Measure.request(
        baseUrl,
        s"/process/answers/comparison?hypothesis=$question/yes&a=${Holders(0)}&b=${Holders(1)}",
        None
      )
      answered = status == 200 && {
        val answer = ujson.read(body)
        answer("answer").obj.get("difference").isDefined && answer("acts").arr.nonEmpty
      }
      if !answered then Thread.sleep(100)
    if !answered then sys.error("the comparison of the two holders was not answered from the graph")
    Result(question, runs.result(), runTimes.result(), records.result(), Measure.millis(started))

  def main(args: Array[String]): Unit =
    val baseUrl = if args.nonEmpty then args(0) else "http://localhost:9000"
    val run     = s"g${java.lang.Long.toString(System.currentTimeMillis() / 1000, 36)}"
    println(s"run $run against $baseUrl")
    val result = measure(baseUrl, run)
    val times  = result.recordTimes
    println(
      s"the four runs: ${result.runMillis.mkString(", ")} ms; " +
        s"${result.records.size} records to the graph with their acts, from each run's end: " +
        s"median ${Measure.within(times, 0.5)} ms, nine in ten within ${Measure.within(times, 0.9)} ms, " +
        s"slowest ${times.last} ms (target: five seconds, nine in ten)"
    )
    println(
      s"the whole set, from the first run started to the two holders compared from the graph: " +
        s"${result.wholeMillis} ms (target: two minutes)"
    )
    if Measure.within(times, 0.9) > 5000L || result.wholeMillis > 120000L then sys.exit(1)
