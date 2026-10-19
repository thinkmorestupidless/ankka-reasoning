package reasoning.service

import com.thinkmorestupidless.ankka.agent.judgment.{
  Judgment,
  JudgmentFailed,
  JudgmentProvider,
  JudgmentRequest,
  JevProvider
}
import com.thinkmorestupidless.ankka.agent.{
  AnthropicProvider,
  ModelProvider,
  ModelRequest,
  ModelResponse
}

import scala.concurrent.Future

/**
 * The text model and the judgment a run asks, from the environment: Anthropic's when its key is
 * set, Jev's when its key is. Without a key the service still starts, and a run that asks says
 * which key is missing.
 */
object Providers:

  val ModelKey: String    = "ANTHROPIC_API_KEY"
  val JudgmentKey: String = JevProvider.KeyVariable

  private def set(env: Map[String, String], key: String): Boolean =
    env.get(key).exists(_.trim.nonEmpty)

  private def missing(key: String): String = s"the model key $key is not set"

  def model(env: Map[String, String] = sys.env): ModelProvider =
    if set(env, ModelKey) then AnthropicProvider.fromEnv()
    else
      new ModelProvider:
        def name: String      = "none"
        def modelName: String = "none"
        def complete(request: ModelRequest): Future[ModelResponse] =
          Future.failed(IllegalStateException(missing(ModelKey)))

  def judgments(env: Map[String, String] = sys.env): JudgmentProvider =
    if set(env, JudgmentKey) then JevProvider.fromEnv(env = env)
    else
      new JudgmentProvider:
        def name: String      = "none"
        def modelName: String = "none"
        def judge(request: JudgmentRequest): Future[Judgment] =
          Future.failed(JudgmentFailed(name, missing(JudgmentKey)))
