package io.chrisdavenport.circuit

import munit.FunSuite
import scala.concurrent.duration._

class BackoffTests extends FunSuite {

  test("validate parameters") {
    intercept[IllegalArgumentException] {
      // Positive increment
      Backoff.additive(-1.seconds)
    }
    intercept[IllegalArgumentException] {
      // Non-zero increment
      Backoff.additive(0.seconds)
    }
    intercept[IllegalArgumentException] {
      // Positive constant value
      Backoff.constant(-1.seconds)
    }
  }
}
