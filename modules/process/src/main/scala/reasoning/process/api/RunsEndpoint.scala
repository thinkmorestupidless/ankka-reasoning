package reasoning.process.api

import com.thinkmorestupidless.ankka.agent.Json
import com.thinkmorestupidless.ankka.agent.blueprint.{RunSnapshot, Shape}
import com.thinkmorestupidless.ankka.core.{CommandError, ErrorCode}
import com.thinkmorestupidless.ankka.http.*
import com.thinkmorestupidless.ankka.runtime.ViewClient
import reasoning.belief.api.Replies
import reasoning.belief.domain.{Refused, Rules}
import reasoning.process.api.ProcessJson.given
import reasoning.process.application.{ActRows, ProcessRecorder}
import reasoning.process.domain.*
import reasoning.process.tools.{RunInput, RunResolver}

/** Starting a run of a blueprint the service carries: ankka's, with their input shapes. */
trait RunStarter:
  def carried: Map[String, Shape]
  def start(blueprint: String, input: Json, runId: String, startedBy: String): RunSnapshot

/** Runs: started here, read back with their endings and their acts. */
final class RunsEndpoint(
    recorder: ProcessRecorder,
    views: ViewClient,
    starter: RunStarter,
    resolver: RunResolver,
    val acl: Acl
) extends HttpEndpoint("/process/runs"):

  postBody("/") { (request: StartRun) =>
    Replies.handle {
      Rules.require(
        ProcessRules.blueprintNotHeld(request.blueprint, starter.carried.keySet),
        Rules.id("run", request.runId, Ids.RunIdLimit),
        ProcessRules.runIdFormat(request.runId),
        starter
          .carried(request.blueprint)
          .check(request.input)
          .headOption
          .map(p => ProcessRules.inputInvalid(p.path.stripPrefix("$.").stripPrefix("$"), p.message))
      )
      val input = RunInput.parse(request.input)
      Rules.require(
        Rules.speaksFor(
          recorder.belief.holder(input.holder),
          Ids.blueprintWriter(request.blueprint)
        )
      )
      val created = recorder.findRun(request.runId).isEmpty
      try starter.start(request.blueprint, request.input, request.runId, principal.subject): Unit
      catch
        case error: CommandError if error.code == ErrorCode.Conflict =>
          throw new Refused(ProcessRules.runIdHeldByAnother(request.runId))
      // Read back rather than trusting the start's answer, which ankka gives from before the
      // run's first event and so with no blueprint, version or start time.
      val run = recorder
        .findRun(request.runId)
        .getOrElse(resolver.holdRun(resolver.snapshot(request.runId), request.runId))
      Replies.recorded(run, created)
    }
  }

  get("/{run}") { (id: String) =>
    Replies.handle {
      val run = recorder.findRun(id).getOrElse(throw Rules.notHeld("run", id))
      Replies.ok(RunView(run, recorder.findEnding(id), acts(id)))
    }
  }

  get("/{run}/acts") { (id: String) =>
    Replies.handle {
      if recorder.findRun(id).isEmpty then throw Rules.notHeld("run", id)
      Replies.ok(ActsView(acts(id)))
    }
  }

  /** A run's acts, from the rows and then each as held; the rows may lag a write by a moment. */
  private def acts(run: String): Vector[Act] =
    ActRows.byRun(views, run).flatMap(row => recorder.findAct(row.id))
