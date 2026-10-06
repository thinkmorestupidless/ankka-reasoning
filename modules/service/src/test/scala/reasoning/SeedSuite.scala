package reasoning

import com.thinkmorestupidless.ankka.http.{Caller, LocalCallers}
import reasoning.seed.Seed
import reasoning.support.ServiceFixture

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt

/** The launch example as a seed file: sent once it is recorded, sent again it changes nothing. */
class SeedSuite extends munit.FunSuite:

  override val munitTimeout = 5.minutes

  private val file    = Paths.get("seed/launch-example.json")
  private val headers = Map(LocalCallers.header(Caller.Service("test", "seed")))

  test("the launch example is recorded, and sent twice it is recorded once") {
    val first = Seed.run(file, ServiceFixture.baseUrl, headers)
    assertEquals(first.size, 10)
    first.foreach(s => assertEquals(s.status, 201, s"${s.path}: ${s.body}"))

    val second = Seed.run(file, ServiceFixture.baseUrl, headers)
    assertEquals(second.size, 10)
    second.foreach(s => assertEquals(s.status, 200, s"${s.path}: ${s.body}"))
    assertEquals(
      second.map(_.body),
      first.map(_.body),
      "a record sent again is answered with the record held"
    )

    val head = ServiceFixture.http.get("/beliefs/current?holder=agent-a&hypothesis=launch/yes").json
    assertEquals(head("count").num, 2.0)
    assertEquals(head("current")("id").str, "agent-a.launch.2")
  }

  test("a seed file stops at the first refusal") {
    val broken = java.nio.file.Files.createTempFile("seed", ".json")
    java.nio.file.Files.writeString(
      broken,
      """{"steps":[{"post":"/holders","body":{"id":"seed-oracle","kind":"oracle","name":"x"}},{"post":"/sources","body":{"id":"seed-never","name":"x"}}]}"""
    ): Unit
    val sent = Seed.run(broken, ServiceFixture.baseUrl, headers)
    assertEquals(sent.map(_.status), Vector(422))
    assertEquals(ServiceFixture.http.get("/sources/seed-never").status, 404)
  }
