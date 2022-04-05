package io.chrisdavenport.circuit

import scala.concurrent.duration._

/** Default functions for calculating the next reset timeout. */
object Backoff {

  /** Doubles the previous duration on each iteration. */
  val exponential: FiniteDuration => FiniteDuration = { last => last + last }

  /** Adds a constant duration on each iteration
   *
   *  @param increment A finite duration that must be greater than zero.
   */
  def additive(increment: FiniteDuration): FiniteDuration => FiniteDuration = {
    require(increment > Duration.Zero)
    _ + increment
  }

  /** Returns a constant duration on each iteration
   *
   *  @param value A finite duration that is zero or greater.
   */
  def constant(value: FiniteDuration): FiniteDuration => FiniteDuration = {
    require(value >= Duration.Zero)
    _ => value
  }

  /** Returns the previously used duration
   *
   *  This is useful if you want your wait time to always be the same as
   *  the inital reset timeout.
   */
  def repeated: FiniteDuration => FiniteDuration = identity
}
