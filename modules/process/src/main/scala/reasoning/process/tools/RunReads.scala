package reasoning.process.tools

import com.thinkmorestupidless.ankka.agent.AgentRuntime
import com.thinkmorestupidless.ankka.agent.blueprint.{Blueprint, RunSnapshot, VersionInfo}
import com.thinkmorestupidless.ankka.core.{CommandError, ErrorCode}

/**
 * What the process layer reads of ankka's own records of blueprints and runs: enough to hold a
 * version and a run the service has not followed yet. A suite stubs it; the service reads ankka.
 */
trait RunReads:
  def run(id: String): Option[RunSnapshot]
  def versionInfo(blueprint: String, version: Int): Option[VersionInfo]
  def version(blueprint: String, version: Int): Option[Blueprint]

object RunReads:

  /** ankka's records, through the runtime once it has started. */
  def of(agents: => AgentRuntime): RunReads = new RunReads:
    private def held[A](read: => A): Option[A] =
      try Some(read)
      catch case error: CommandError if error.code == ErrorCode.NotFound => None
    def run(id: String): Option[RunSnapshot] = held(agents.runs.get(id))
    def versionInfo(blueprint: String, version: Int): Option[VersionInfo] =
      held(agents.blueprints.versions(blueprint)).flatMap(_.find(_.number == version))
    def version(blueprint: String, version: Int): Option[Blueprint] =
      held(agents.blueprints.version(blueprint, version))
