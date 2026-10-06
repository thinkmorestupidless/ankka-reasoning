package reasoning

import reasoning.support.{Eventually, Http, LaunchExample, ServiceFixture}

import scala.concurrent.duration.DurationInt

/** The launch example, recorded and read back through the service alone. */
class LaunchExampleSuite extends munit.FunSuite:

  override val munitTimeout = 5.minutes

  private lazy val http: Http = ServiceFixture.http

  test("the launch example is recorded and read back as it was stated") {
    val example = LaunchExample(http, "smoke").record()

    val question = http.get(s"/questions/${example.question}").json
    assertEquals(question("hypotheses").arr.map(_("id").str).toList, List("yes", "no"))

    val claim = http.get(s"/claims/${example.barrierGone}").json("claim")
    assertEquals(claim("statement").str, example.BarrierGoneStatement)
    assertEquals(claim("revises").str, example.pending)
    assertEquals(claim("writer").str, Http.writer(Http.Writer))

    val head = http.get(s"/beliefs/current?holder=${example.holder}&hypothesis=${example.yes}").json
    assertEquals(head("count").num, 2.0)
    assertEquals(head("current")("probability").num, 0.61)
    assertEquals(head("current")("follows").str, example.first)

    val line = http.get(s"/beliefs/line?holder=${example.holder}&hypothesis=${example.yes}").json
    assertEquals(line("revisions").arr.map(_("probability").num).toList, List(0.61, 0.38))
    assertEquals(line("more").bool, false)
  }

  test("the earlier claim comes to be read as revised by the later one") {
    val example = LaunchExample(http, "smoke")
    Eventually.eventually() {
      val listed = http.get(s"/claims/${example.pending}").json("revisedBy").arr.map(_.str).toList
      assertEquals(listed, List(example.barrierGone))
    }
  }
