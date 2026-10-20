package reasoning.process.api

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.thinkmorestupidless.ankka.agent.{AgentRuntime, Json}
import com.thinkmorestupidless.ankka.http.*
import reasoning.belief.api.Replies
import reasoning.belief.domain.{Moment, Refused, Rules}
import reasoning.process.application.ProcessRecorder
import reasoning.process.appraiser.{Appraiser, Instructions}
import reasoning.process.domain.{Ids, ProcessRules}

/** What `POST /process/appraiser/versions` is sent: the name of an instruction set. */
final case class RegisterVersion(instructions: String)

/** A version of the appraiser as ankka holds it. */
final case class VersionSummary(blueprint: String, version: Int, digest: String, dated: Moment)

final case class VersionsView(versions: Vector[VersionSummary])

/** The reply to a registration: the version the instructions are at. */
final case class VersionRegistered(blueprint: String, version: Int, dated: Moment)

/** A version as this service holds it, with ankka's canonical blueprint as `blueprint`. */
final case class VersionDetail(
    id: String,
    blueprint: Json,
    name: String,
    version: Int,
    digest: String,
    dated: Moment,
    recordedAt: Moment,
    writer: String
)

object AppraiserJson:
  given JsonValueCodec[RegisterVersion]   = Replies.codec[RegisterVersion]
  given JsonValueCodec[VersionSummary]    = Replies.codec[VersionSummary]
  given JsonValueCodec[VersionsView]      = Replies.codec[VersionsView]
  given JsonValueCodec[VersionRegistered] = Replies.codec[VersionRegistered]
  given JsonValueCodec[VersionDetail]     = Replies.codec[VersionDetail]

/**
 * The appraiser's versions: registered again with one of the instruction sets the service carries,
 * listed as ankka holds them, and read one at a time with the canonical blueprint.
 */
final class AppraiserEndpoint(
    recorder: ProcessRecorder,
    agents: => AgentRuntime,
    val acl: Acl
) extends HttpEndpoint("/process/appraiser/versions"):

  import AppraiserJson.given

  postBody("/") { (request: RegisterVersion) =>
    Replies.handle {
      val set = Instructions
        .named(request.instructions)
        .getOrElse(throw new Refused(ProcessRules.instructionsUnknown(request.instructions)))
      val registered = agents.blueprints.register(Appraiser.blueprint(set))
      val dated = agents.blueprints
        .versions(Appraiser.Name)
        .find(_.number == registered.version)
        .map(v => Moment(v.registeredAt))
        .getOrElse(recorder.now())
      Replies.recorded(
        VersionRegistered(Appraiser.Name, registered.version, dated),
        registered.isNew
      )
    }
  }

  get("/") { () =>
    Replies.handle {
      Replies.ok(
        VersionsView(
          agents.blueprints
            .versions(Appraiser.Name)
            .map(v => VersionSummary(Appraiser.Name, v.number, v.digest, Moment(v.registeredAt)))
        )
      )
    }
  }

  get("/{n}") { (n: String) =>
    Replies.handle {
      val id = Ids.version(Appraiser.Name, n.toIntOption.getOrElse(0))
      val held = n.toIntOption
        .flatMap(recorder.findVersion(Appraiser.Name, _))
        .getOrElse(throw Rules.notHeld("blueprint-version", id))
      val canonical = Json
        .parse(agents.blueprints.version(Appraiser.Name, held.version).canonical)
        .fold(_ => Json.Null, identity)
      Replies.ok(
        VersionDetail(
          held.id,
          canonical,
          held.blueprint,
          held.version,
          held.digest,
          held.dated,
          held.recordedAt,
          held.writer
        )
      )
    }
  }
