package reasoning.process.domain

/**
 * Whether a record sent again is the record already held. It compares what ankka supplied, or what
 * the run did, and nothing the service set: not the time recorded, not the writer.
 */
object Same:

  def version(held: BlueprintVersion, sent: BlueprintVersion): Boolean =
    held.blueprint == sent.blueprint && held.version == sent.version &&
      held.digest == sent.digest && held.dated == sent.dated

  def run(held: Run, sent: Run): Boolean =
    held.blueprint == sent.blueprint && held.version == sent.version &&
      held.startedBy == sent.startedBy && held.dated == sent.dated

  def ending(held: RunEnding, sent: RunEnding): Boolean =
    held.status == sent.status && held.reason == sent.reason && held.dated == sent.dated

  def act(held: Act, sent: Act): Boolean =
    held.run == sent.run && held.step == sent.step && held.worker == sent.worker &&
      held.blueprint == sent.blueprint && held.version == sent.version &&
      held.record == sent.record && held.holder == sent.holder && held.dated == sent.dated
