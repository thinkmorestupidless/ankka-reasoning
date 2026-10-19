package reasoning.process.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.Json
import reasoning.belief.api.Replies
import reasoning.process.domain.*

/** What `POST /process/runs` is sent. */
final case class StartRun(blueprint: String, runId: String, input: Json)

/** A run, its ending once it has one, and its acts. */
final case class RunView(run: Run, ending: Option[RunEnding], acts: Vector[Act])

final case class ActsView(acts: Vector[Act])

final case class ActView(act: Option[Act])

object ProcessJson:
  given JsonValueCodec[BlueprintVersion] = Replies.codec[BlueprintVersion]
  given JsonValueCodec[Run]              = Replies.codec[Run]
  given JsonValueCodec[RunEnding]        = Replies.codec[RunEnding]
  given JsonValueCodec[Act]              = Replies.codec[Act]
  given JsonValueCodec[StartRun]         = Replies.codec[StartRun]
  given JsonValueCodec[RunView]          = Replies.codec[RunView]
  given JsonValueCodec[ActsView]         = Replies.codec[ActsView]
  given JsonValueCodec[ActView]          = Replies.codec[ActView]
