package reasoning.support

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** Retries a check until it holds or time runs out, then fails with the last reason it did not. */
object Eventually:

  def eventually[A](within: FiniteDuration = 30.seconds)(check: => A): A =
    val deadline                = within.fromNow
    var last: Option[Throwable] = None
    var result: Option[A]       = None
    while result.isEmpty && (last.isEmpty || deadline.hasTimeLeft()) do
      try result = Some(check)
      catch
        case failure: (AssertionError | munit.FailExceptionLike[?] | NoSuchElementException) =>
          last = Some(failure)
          Thread.sleep(200)
    result.getOrElse(throw last.get)
