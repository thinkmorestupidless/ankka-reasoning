package reasoning.support

import ujson.{Arr, Obj}

/**
 * The launch example of the glossary, recorded through the service under identifiers of one
 * scenario's own, so that no scenario sees another's records.
 *
 * @param p
 *   a prefix for every identifier; short, since an identifier is at most 64 characters
 */
final class LaunchExample(
    http: Http,
    p: String,
    clock: Option[reasoning.belief.application.SettableClock] = None
):

  /** Sets the service's clock, when the example is given one: each day is recorded on that day. */
  private def today(iso: String): Unit =
    clock.foreach(_.set(reasoning.belief.domain.Moment.parse(iso).get))

  val question    = s"$p-launch"
  val yes         = s"$question/yes"
  val no          = s"$question/no"
  val holder      = s"$p-agent-a"
  val filings     = s"$p-company-x-filings"
  val regulator   = s"$p-regulator"
  val pending     = s"$p-pending"
  val barrierGone = s"$p-barrier-gone"
  val first       = s"$p-agent-a.1"
  val second      = s"$p-agent-a.2"

  val PendingStatement     = "approval is pending and the launch date is uncertain"
  val BarrierGoneStatement = "the approval barrier has gone"

  val May4  = "2026-05-04T09:00:00Z"
  val May11 = "2026-05-11T09:00:00Z"

  /** The identifiers of the two pieces of evidence, known once they are recorded. */
  var filing: String = ""
  var notice: String = ""

  private def created(answer: Answer): Answer =
    assert(
      answer.status == 201 || answer.status == 200,
      s"the launch example was refused: ${answer.body}"
    )
    answer

  /** The question, the holder and the two sources: what both days need. */
  def open(): Unit =
    today("2026-05-01T12:00:00Z")
    created(
      http.post(
        "/questions",
        Obj(
          "id"        -> question,
          "statement" -> "Will Company X launch Product Y this year?",
          "hypotheses" -> Arr(
            Obj("id" -> "yes", "statement" -> "it launches this year"),
            Obj("id" -> "no", "statement"  -> "it does not launch this year")
          ),
          "dated" -> "2026-05-01T09:00:00Z"
        )
      )
    ): Unit
    created(
      http.post("/holders", Obj("id" -> holder, "kind" -> "agent", "name" -> "agent-a"))
    ): Unit
    created(http.post("/sources", Obj("id" -> filings, "name" -> "Company X filings"))): Unit
    created(http.post("/sources", Obj("id" -> regulator, "name" -> "the regulator"))): Unit

  /** 4 May: the filing, the claim that approval is pending, and the belief at 0.38. */
  def may4(): Unit =
    today("2026-05-04T12:00:00Z")
    filing = created(
      http.post(
        "/evidence",
        Obj(
          "source"  -> filings,
          "locator" -> s"https://filings.example/$p/2026-q1",
          "excerpt" -> "Regulatory approval for Product Y remains pending; no launch date is committed.",
          "author"     -> "Company X",
          "observedAt" -> May4
        )
      )
    ).json("id").str
    created(
      http.post(
        "/claims",
        Obj(
          "id"          -> pending,
          "holder"      -> holder,
          "statement"   -> PendingStatement,
          "derivesFrom" -> Arr(filing),
          "stances"     -> Arr(Obj("hypothesis" -> yes, "stance" -> "contradicts")),
          "dated"       -> May4
        )
      )
    ): Unit
    created(
      http.post(
        "/beliefs/revisions",
        Obj(
          "id"          -> first,
          "holder"      -> holder,
          "hypothesis"  -> yes,
          "probability" -> 0.38,
          "restsOn"     -> Arr(Obj("claim" -> pending)),
          "dated"       -> May4
        )
      )
    ): Unit

  /** 11 May: the notice, the claim that revises the first, and the belief at 0.61. */
  def may11(): Unit =
    today("2026-05-11T12:00:00Z")
    notice = created(
      http.post(
        "/evidence",
        Obj(
          "source"     -> regulator,
          "locator"    -> s"https://regulator.example/$p/notices/1187",
          "excerpt"    -> "Notice 1187: Product Y is approved for sale.",
          "observedAt" -> May11
        )
      )
    ).json("id").str
    created(
      http.post(
        "/claims",
        Obj(
          "id"          -> barrierGone,
          "holder"      -> holder,
          "statement"   -> BarrierGoneStatement,
          "derivesFrom" -> Arr(notice),
          "stances"     -> Arr(Obj("hypothesis" -> yes, "stance" -> "supports")),
          "revises"     -> pending,
          "dated"       -> May11
        )
      )
    ): Unit
    created(
      http.post(
        "/beliefs/revisions",
        Obj(
          "id"          -> second,
          "holder"      -> holder,
          "hypothesis"  -> yes,
          "probability" -> 0.61,
          "restsOn"     -> Arr(Obj("claim" -> barrierGone)),
          "follows"     -> first,
          "dated"       -> May11
        )
      )
    ): Unit

  def record(): LaunchExample =
    open()
    may4()
    may11()
    this
