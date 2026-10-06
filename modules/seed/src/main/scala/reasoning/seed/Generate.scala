package reasoning.seed

import ujson.{Arr, Obj}

import java.nio.file.{Files, Paths}
import java.time.{LocalDate, ZoneOffset}

/**
 * The seeded set: ten questions and the reasoning about them, as steps a seed file holds. It is
 * generated so that it can be large and still be the same every time; `seed/ten-questions.json` is
 * its output, committed. Every name, number and date in it is made up.
 *
 * Each question has fourteen pieces of evidence over six weeks, twelve claims by three holders,
 * four of which revise an earlier claim of the same holder, and seven revisions of each holder's
 * belief, some resting on another holder's claim. Three questions have a market with twenty price
 * observations, and one of those is resolved and its resolution revised. The text of one piece of
 * evidence and of one claim is withdrawn at the end.
 */
object Generate:

  private val Questions = Vector(
    "bridge"    -> "Will the Northgate bridge reopen before winter?",
    "ferry"     -> "Will the Saltmarsh ferry run a night service this year?",
    "harvest"   -> "Will the Low Fields harvest exceed last year's?",
    "tram"      -> "Will the Quay Street tram extension open on its planned date?",
    "library"   -> "Will the Old Mill library keep its Sunday hours?",
    "reservoir" -> "Will the Hollow Tarn reservoir be full by midsummer?",
    "festival"  -> "Will the Lantern festival sell every ticket?",
    "orchard"   -> "Will the Westbank orchard be replanted this season?",
    "signal"    -> "Will the Ridge signal box be automated by the year's end?",
    "tunnel"    -> "Will the Greywater tunnel survey finish on time?"
  )

  private val Holders = Vector(
    ("analyst-a", "agent", "analyst A"),
    ("analyst-b", "agent", "analyst B"),
    ("model-m", "model", "model M"),
    ("reader-r", "person", "reader R")
  )

  private val Sources = Vector(
    "harbour-gazette"   -> "the Harbour Gazette",
    "ministry-bulletin" -> "the ministry bulletin",
    "trade-register"    -> "the trade register",
    "observatory-log"   -> "the observatory log",
    "council-minutes"   -> "the council minutes",
    "exchange-notices"  -> "the Lantern exchange notices"
  )

  private val Findings = Vector(
    "the works are reported ahead of the published schedule",
    "an inspection found a fault that has to be put right first",
    "funding for the remaining stage was confirmed in full",
    "the contractor asked for more time",
    "a second crew was assigned",
    "supplies were held up at the depot",
    "the committee approved the plan without amendment",
    "an objection was lodged and has to be heard",
    "the weather allowed work on every day of the period",
    "two weeks were lost to flooding",
    "the trial run passed",
    "the trial run was put back",
    "the last permit was granted",
    "a permit is still outstanding"
  )

  val EvidencePerQuestion   = 14
  val ClaimsPerQuestion     = 12
  val RevisionsPerHolder    = 7
  val ObservationsPerMarket = 20
  val Markets               = 3

  /** The claims that revise the claim three before them, which the same holder made. */
  private val Revising = Set(3, 6, 7, 10)

  private val Start = LocalDate.of(2026, 3, 2)

  private def at(day: Long, hour: Int): String =
    Start.plusDays(day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toString

  /** A number from 0.05 to 0.95 in hundredths, the same for the same three inputs. */
  private def chance(a: Int, b: Int, c: Int): Double =
    val mixed =
      java.lang.Integer.remainderUnsigned((a * 73856093) ^ (b * 19349663) ^ (c * 83492791), 91)
    (5 + mixed) / 100.0

  private def step(path: String, body: Obj, as: Option[String] = None): Obj =
    val one = Obj("post" -> path, "body" -> body)
    as.foreach(name => one("as") = name)
    one

  /**
   * @param prefix
   *   put before every identifier, so the set can be recorded beside itself; empty for the file
   */
  def steps(prefix: String = ""): Vector[Obj] =
    val holders = Holders.map((id, kind, name) =>
      step("/holders", Obj("id" -> s"$prefix$id", "kind" -> kind, "name" -> name))
    )
    val sources =
      Sources.map((id, name) => step("/sources", Obj("id" -> s"$prefix$id", "name" -> name)))
    val questions = Questions.zipWithIndex.flatMap { case ((slug, statement), q) =>
      question(prefix, slug, statement, q)
    }
    holders ++ sources ++ questions ++ withdrawals(prefix)

  private def evidenceName(slug: String, k: Int)            = s"ev.$slug.$k"
  private def claimId(prefix: String, slug: String, j: Int) = s"$prefix$slug.c${j + 1}"
  private def revisionId(prefix: String, slug: String, holder: Int, r: Int) =
    s"$prefix${Holders(holder)._1}.$slug.${r + 1}"

  private def question(prefix: String, slug: String, statement: String, q: Int): Vector[Obj] =
    val id   = s"$prefix$slug"
    val base = q.toLong // each question starts a day after the one before
    val open = step(
      "/questions",
      Obj(
        "id"        -> id,
        "statement" -> statement,
        "hypotheses" -> Arr(
          Obj("id" -> "yes", "statement" -> "it does"),
          Obj("id" -> "no", "statement"  -> "it does not")
        ),
        "dated" -> at(base, 7)
      )
    )

    // Evidence k is observed three days after the one before it.
    val evidence = (0 until EvidencePerQuestion).map { k =>
      val (source, name) = Sources((q + k) % Sources.size)
      step(
        "/evidence",
        Obj(
          "source"  -> s"$prefix$source",
          "locator" -> s"https://records.example/$source/$slug/${k + 1}",
          "excerpt" -> s"${name.capitalize}, entry ${k + 1} on the question '$statement': ${Findings((q + k) % Findings.size)}.",
          "publishedAt" -> at(base + 3L * k, 7),
          "observedAt"  -> at(base + 3L * k, 8)
        ),
        as = Some(evidenceName(slug, k))
      )
    }

    // Claim j is stated an hour after evidence j by holder j mod 3, from that evidence and, after
    // the first, the one before it. It supports "yes" when the finding was good news.
    val claims = (0 until ClaimsPerQuestion).map { j =>
      val good    = (q + j) % 2 == 0
      val finding = Findings((q + j) % Findings.size)
      val body = Obj(
        "id"     -> claimId(prefix, slug, j),
        "holder" -> s"$prefix${Holders(j % 3)._1}",
        "statement" -> s"On '$statement': $finding, which makes it ${
            if good then "more" else "less"
          } likely.",
        "derivesFrom" -> Arr.from(
          (if j == 0 then Seq(j) else Seq(j, j - 1)).map(k => s"@${evidenceName(slug, k)}")
        ),
        "stances" -> Arr(
          Obj("hypothesis" -> s"$id/yes", "stance" -> (if good then "supports" else "contradicts")),
          Obj("hypothesis" -> s"$id/no", "stance"  -> (if good then "contradicts" else "supports"))
        ),
        "dated" -> at(base + 3L * j, 9)
      )
      if Revising(j) then body("revises") = claimId(prefix, slug, j - 3)
      step("/claims", body)
    }

    // Holder h revises its belief every six days, at noon, resting on its own two latest claims
    // and, every other time, on the latest claim of the holder beside it.
    def latest(holder: Int, day: Long, count: Int): Seq[Int] =
      (0 until ClaimsPerQuestion).filter(j => j % 3 == holder && 3L * j <= day).takeRight(count)
    val revisions = for
      r <- 0 until RevisionsPerHolder
      h <- 0 until 3
    yield
      val day    = 6L * r + h + 4
      val own    = latest(h, day, 2)
      val beside = if r % 2 == 1 then latest((h + 1) % 3, day, 1) else Seq.empty
      val restsOn = (own ++ beside).zipWithIndex.map { (j, i) =>
        val rest = Obj("claim" -> claimId(prefix, slug, j))
        if (r + i) % 2 == 0 then rest("weight") = chance(q, j, r)
        rest
      }
      val body = Obj(
        "id"          -> revisionId(prefix, slug, h, r),
        "holder"      -> s"$prefix${Holders(h)._1}",
        "hypothesis"  -> s"$id/yes",
        "probability" -> chance(q, h, r),
        "restsOn"     -> Arr.from(restsOn),
        "dated"       -> at(base + day, 12)
      )
      if r > 0 then body("follows") = revisionId(prefix, slug, h, r - 1)
      step("/beliefs/revisions", body)

    open +: (interleave(evidence, claims) ++ revisions ++ (if q < Markets then
                                                             market(prefix, slug, id, q)
                                                           else Vector.empty))

  /**
   * Evidence and claims in the order they happened: each claim after the evidence it derives from.
   */
  private def interleave(evidence: Seq[Obj], claims: Seq[Obj]): Vector[Obj] =
    evidence.indices.toVector.flatMap(i => evidence(i) +: claims.lift(i).toVector)

  private def market(prefix: String, slug: String, question: String, q: Int): Vector[Obj] =
    val id   = s"$prefix$slug-market"
    val base = q.toLong
    val open = step(
      "/markets",
      Obj(
        "id"       -> id,
        "question" -> question,
        "venue"    -> "the Lantern exchange",
        "outcomes" -> Arr(
          Obj("outcome" -> "YES", "hypothesis" -> s"$question/yes"),
          Obj("outcome" -> "NO", "hypothesis"  -> s"$question/no")
        ),
        "resolutionCriteria" -> "Resolves YES when the council's minutes record it as done, and NO at the year's end otherwise.",
        "closesAt" -> at(base + 60, 17),
        "dated"    -> at(base + 1, 9)
      )
    )
    // A price every two days; never the same twice running, so each is a revision of its own.
    val observations = (0 until ObservationsPerMarket).map { k =>
      val price = chance(q, 100 + k, 7)
      val moved = if k > 0 && price == chance(q, 99 + k, 7) then (price + 0.01).min(0.99) else price
      step(
        s"/markets/$id/price-observations",
        Obj("outcome" -> "YES", "price" -> moved, "observedAt" -> at(base + 2 + 2L * k, 16))
      )
    }
    // The first market is resolved, and the resolution then revised on later evidence.
    def resolved(
        name: String,
        outcome: String,
        evidence: Int,
        day: Long,
        revises: Option[String]
    ): Obj =
      val body = Obj(
        "id"        -> name,
        "outcome"   -> outcome,
        "evidence"  -> Arr(s"@${evidenceName(slug, evidence)}"),
        "authority" -> "the Lantern exchange",
        "dated"     -> at(base + day, 10)
      )
      revises.foreach(body("revises") = _)
      step(s"/markets/$id/resolutions", body)
    val resolution =
      if q != 0 then Vector.empty
      else
        Vector(
          resolved("first", "NO", EvidencePerQuestion - 2, 61, None),
          resolved("second", "YES", EvidencePerQuestion - 1, 62, Some("first"))
        )
    open +: (observations.toVector ++ resolution)

  /** One piece of evidence and one claim have their text taken out, as a mistake put right. */
  private def withdrawals(prefix: String): Vector[Obj] =
    Vector(
      step(
        s"/evidence/{@${evidenceName("library", 4)}}/withdrawal",
        Obj("note" -> "quoted more of the minutes than may be kept")
      ),
      step(
        s"/claims/${claimId(prefix, "orchard", 5)}/withdrawal",
        Obj("note" -> "named a person who asked not to be named")
      )
    )

  /** How many records the steps make, a hypothesis counted with its question. */
  def records(steps: Vector[Obj]): Int = steps.count(!_("post").str.endsWith("/withdrawal"))

  def main(args: Array[String]): Unit =
    val out = Paths.get(if args.nonEmpty then args(0) else "seed/ten-questions.json")
    val all = steps()
    val file = Obj(
      "name" -> "ten-questions",
      "about" -> "Ten questions and the reasoning about them, written by reasoning.seed.Generate. Every name, number and date in it is made up. Do not edit: run `sbt \"seed/runMain reasoning.seed.Generate\"`.",
      "steps" -> Arr.from(all)
    )
    Files.writeString(out, ujson.write(file, indent = 2) + "\n"): Unit
    println(s"$out: ${all.size} steps, ${records(all)} records")
