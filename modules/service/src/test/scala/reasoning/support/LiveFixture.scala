package reasoning.support

import com.thinkmorestupidless.ankka.agent.judgment.{Judgment, JudgmentProvider, JudgmentRequest}
import com.thinkmorestupidless.ankka.agent.{
  AgentRuntime,
  ModelChunk,
  ModelProvider,
  ModelRequest,
  ModelResponse
}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import reasoning.belief.application.SettableClock
import reasoning.service.Providers

import scala.concurrent.Future

/**
 * The service the live suite runs against: the graph fixture's, since what the suite asserts is
 * read from the graph, with its text model and its judgment switched to the real providers while a
 * live run is on. Nothing here starts, and no provider is made, unless both keys are set and a
 * suite asks; the build never sets them.
 *
 * Every run in the graph fixture's JVM asks `model` and `judgments`. Outside `within` they are the
 * scripted providers every other suite scripts; inside it they are Anthropic's and Jev's, from the
 * environment.
 */
object LiveFixture:

  val Keys: Vector[String] = Vector(Providers.ModelKey, Providers.JudgmentKey)

  /** Whether both keys are set in the given environment; the test JVM's by default. */
  def keysSet(env: Map[String, String] = sys.env): Boolean =
    Keys.forall(key => env.get(key).exists(_.trim.nonEmpty))

  def missing(env: Map[String, String] = sys.env): String =
    s"the live suite needs ${Keys.mkString(" and ")} set; " +
      s"not set: ${Keys.filterNot(key => env.get(key).exists(_.trim.nonEmpty)).mkString(", ")}"

  @volatile private var live: Boolean = false

  private lazy val realModel: ModelProvider        = Providers.model()
  private lazy val realJudgments: JudgmentProvider = Providers.judgments()

  private def currentModel: ModelProvider =
    if live then realModel else ServiceFixture.model
  private def currentJudgments: JudgmentProvider =
    if live then realJudgments else ServiceFixture.judge

  /** The text model the graph fixture's runs ask: scripted, or the real one within a live run. */
  val model: ModelProvider = new ModelProvider:
    def name: String                                           = currentModel.name
    def modelName: String                                      = currentModel.modelName
    def complete(request: ModelRequest): Future[ModelResponse] = currentModel.complete(request)
    override def stream(request: ModelRequest): Source[ModelChunk, NotUsed] =
      currentModel.stream(request)

  /** The judgment the graph fixture's runs ask: scripted, or Jev within a live run. */
  val judgments: JudgmentProvider = new JudgmentProvider:
    def name: String                                      = currentJudgments.name
    def modelName: String                                 = currentJudgments.modelName
    def judge(request: JudgmentRequest): Future[Judgment] = currentJudgments.judge(request)

  /** Runs `body` with the real providers answering, and puts the scripted ones back after. */
  def within[A](body: => A): A =
    require(keysSet(), missing())
    live = true
    try body
    finally live = false

  def http: Http           = GraphFixture.http
  def clock: SettableClock = GraphFixture.clock
  def agents: AgentRuntime = GraphFixture.agents
