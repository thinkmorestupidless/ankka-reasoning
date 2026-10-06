package reasoning.belief.application

import reasoning.belief.domain.Moment

import java.util.concurrent.atomic.AtomicLong

/**
 * The service's one source of "now". A handler never reads a clock: the endpoint reads this once
 * and the time travels in the command.
 */
trait Clock:
  def now(): Moment

object Clock:
  val system: Clock = () => Moment(System.currentTimeMillis())

/** A clock a test sets, so that a record can be dated one day and recorded on another. */
final class SettableClock(start: Moment) extends Clock:
  private val current       = AtomicLong(start.millis)
  def now(): Moment         = Moment(current.get())
  def set(to: Moment): Unit = current.set(to.millis)
