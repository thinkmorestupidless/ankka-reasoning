package reasoning.process.domain

import reasoning.belief.domain.{HypothesisRef, Ids as BeliefIds}

import java.security.MessageDigest
import java.util.HexFormat

/**
 * The identifiers of the process layer, and of the records a run writes. Each is derived from what
 * the run already knows, so a call made again names the same record and is a repeat.
 */
object Ids:

  /**
   * The longest run id whose records' ids fit the belief layer's limit: a claim's id is the run id,
   * a dot and 16 hex characters.
   */
  val RunIdLimit: Int = BeliefIds.Limit - 17

  /** A run id ankka keeps for scheduled runs. */
  val SchedulePrefix: String =
    com.thinkmorestupidless.ankka.agent.blueprint.RunEntity.SchedulePrefix

  private def sha256(text: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8")))

  def version(blueprint: String, n: Int): String = s"$blueprint@$n"

  /**
   * A record has one act, whichever run wrote it first: the id is the record's, so a later run that
   * meets the record already held finds its act under the same id and writes none.
   */
  def act(record: RecordRef): String =
    "act:" + sha256(s"${record.kind}\n${record.id}\n").take(56)

  def claim(run: String, statement: String): String = s"$run." + sha256(statement).take(16)

  /** A run revises a belief in a hypothesis once. */
  def revision(run: String, hypothesis: HypothesisRef): String = s"$run.${hypothesis.hypothesis}"
