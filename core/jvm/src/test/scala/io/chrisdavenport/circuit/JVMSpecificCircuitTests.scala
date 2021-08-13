package io.chrisdavenport.circuit

// import cats.syntax.all._
import scala.concurrent.duration._
import cats.effect._
// import cats.effect.syntax._

// import catalysts.Platform
import munit.CatsEffectSuite

class JVMSpecificCircuitTests extends CatsEffectSuite {

  test("Validate onClosed is called when closing from longRunning openOnFail"){
    val test = for {
      cb1 <- CircuitBreaker.of[IO](
        maxFailures = 1,
        resetTimeout = 1.minute,
        exponentialBackoffFactor = 1,
        maxResetTimeout = 1.minute
      )
      closed <- Ref[IO].of(false)
      cb = cb1.doOnClosed(closed.set(true))
      dummy = new RuntimeException("dummy")
      taskInError = cb.protect(IO[Int](throw dummy))
      wait <- Deferred[IO, Unit]
      completed <- Deferred[IO, Unit]
      _ <- (cb.protect(wait.get) >> completed.complete(())).start // Will reset when wait completes
      _ <- taskInError.attempt
      _ <- wait.complete(())
      _ <- completed.get
      didClose <- closed.get
    } yield assertEquals(didClose, true)

    test
  }

}