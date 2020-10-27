/*
 * Initial Copyright
 * 
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Modifications 
 * 
 * Copyright (C) 2019 Christopher Davenport
 * Edits: 
 * 1. Change Package
 * 2. Removed Platform specific testing
 */
package io.chrisdavenport.circuit

import scala.concurrent.{ExecutionContext, Future}

import cats.data.OptionT
import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.{Deferred, Ref}
import org.scalatest.Succeeded
import org.scalatest.funsuite.AsyncFunSuite
import cats.implicits._
import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers

// import catalysts.Platform


class CircuitBreakerTests extends AsyncFunSuite with Matchers {
  private val Tries = 10000 //if (Platform.isJvm) 10000 else 5000

  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global
  /*_*/
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  /*_*/
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)


  private def mkBreaker() = CircuitBreaker.in[OptionT[IO, *], IO](
    maxFailures = 5,
    resetTimeout = 1.minute
  ).value.unsafeRunSync().get


  test("should work for successful async IOs") {
    val circuitBreaker = mkBreaker()

    var effect = 0
    val task = circuitBreaker.protect(IO {
      effect += 1
    } *> IO.shift)

    List.fill(Tries)(task).sequence_.unsafeToFuture().map { _ =>
      effect shouldBe Tries
    }
  }

  test("should work for successful immediate tasks") {
    val circuitBreaker = mkBreaker()

    var effect = 0
    val task = circuitBreaker.protect(IO {
      effect += 1
    })

    List.fill(Tries)(task).sequence_.unsafeToFuture().map { _ =>
      effect shouldBe Tries
    }
  }

  test("should be stack safe for successful async tasks (flatMap)") {
    val circuitBreaker = mkBreaker()

    def loop(n: Int, acc: Int): IO[Int] = {
      if (n > 0)
        circuitBreaker.protect(IO(acc+1) <* IO.shift)
          .flatMap(s => loop(n-1, s))
      else
        IO.pure(acc)
    }

    loop(Tries, 0).unsafeToFuture().map { value =>
      value shouldBe Tries
    }
  }

  test("should be stack safe for successful async tasks (suspend)") {
    val circuitBreaker = mkBreaker()

    def loop(n: Int, acc: Int): IO[Int] =
      IO.suspend {
        if (n > 0)
          circuitBreaker.protect(loop(n-1, acc+1))
        else
          IO.pure(acc)
      } <* IO.shift

    loop(Tries, 0).unsafeToFuture().map { value =>
      value shouldBe Tries
    }
  }

  test("should be stack safe for successful immediate tasks (flatMap)") {
    val circuitBreaker = mkBreaker()

    def loop(n: Int, acc: Int): IO[Int] = {
      if (n > 0)
        circuitBreaker.protect(IO(acc+1))
          .flatMap(s => loop(n-1, s))
      else
        IO.pure(acc)
    }

    loop(Tries, 0).unsafeToFuture().map { value =>
      value shouldBe Tries
    }
  }

  test("should be stack safe for successful immediate tasks (suspend)") {
    val circuitBreaker = mkBreaker()

    def loop(n: Int, acc: Int): IO[Int] =
      IO.suspend {
        if (n > 0)
          circuitBreaker.protect(loop(n-1, acc+1))
        else
          IO.pure(acc)
      }

    loop(Tries, 0).unsafeToFuture().map { value =>
      value shouldBe Tries
    }
  }

  test("complete workflow with failures and exponential backoff") {
    var openedCount = 0
    var closedCount = 0
    var halfOpenCount = 0
    var rejectedCount = 0

    val circuitBreaker = {
      val cb = CircuitBreaker.of[IO](
        maxFailures = 5,
        resetTimeout = 200.millis,
        exponentialBackoffFactor = 2,
        maxResetTimeout = 1.second
      ).unsafeRunSync()

      cb.doOnOpen(IO { openedCount += 1})
        .doOnClosed(IO { closedCount += 1 })
        .doOnHalfOpen(IO { halfOpenCount += 1 })
        .doOnRejected(IO { rejectedCount += 1 })
    }

    def unsafeState() = circuitBreaker.state.unsafeRunSync()

    val dummy = new RuntimeException("dummy")
    val taskInError = circuitBreaker.protect(IO[Int](throw dummy))
    val taskSuccess = circuitBreaker.protect(IO { 1 })
    val fa =
      for {
        _ <- taskInError.attempt
        _ <- taskInError.attempt
        _ = unsafeState() shouldBe CircuitBreaker.Closed(2)
        // A successful value should reset the counter
        _ <- taskSuccess
        _ = unsafeState() shouldBe CircuitBreaker.Closed(0)

        _ <- taskInError.attempt.replicateA(5)
        _ = unsafeState() should matchPattern {
          case CircuitBreaker.Open(_, t) if t == 200.millis =>
        }
        res <- taskSuccess.attempt
        _ = res should matchPattern {
          case Left(_: CircuitBreaker.RejectedExecution) =>
        }
        _ <- IO.sleep(1.nano) // This timeout is intentionally small b/c actuall time is not deterministic
        // Should still fail-fast
        res2 <- taskSuccess.attempt
        _ = res2 should matchPattern {
          case Left(_: CircuitBreaker.RejectedExecution) =>
        }
        _ <- IO.sleep(200.millis)

        // Testing half-open state
        d <- Deferred[IO, Unit]
        fiber <- circuitBreaker.protect(d.get).start
        _ <- IO.sleep(1.second)
        _ = unsafeState() should matchPattern {
          case CircuitBreaker.HalfOpen =>
        }

        // Should reject other tasks

        res3 <- taskSuccess.attempt
        _ = res3 should matchPattern {
          case Left(_: CircuitBreaker.RejectedExecution) =>
        }

        _ <- d.complete(())
        _ <- fiber.join

        // Should re-open on success
        _ = unsafeState() shouldBe CircuitBreaker.Closed(0)

        _ = (openedCount, closedCount, halfOpenCount, rejectedCount) shouldBe ((1, 1, 1, 3))
      } yield Succeeded

    fa.unsafeToFuture()
  }

  test("keep rejecting with consecutive call errors") {

    val circuitBreaker =
      CircuitBreaker.of[IO](
        maxFailures = 2,
        resetTimeout = 100.millis,
        exponentialBackoffFactor = 1,
        maxResetTimeout = 100.millis
      ).unsafeRunSync()

    def unsafeState() = circuitBreaker.state.unsafeRunSync()

    val dummy = new RuntimeException("dummy")
    val taskInError = circuitBreaker.protect(IO[Int](throw dummy))
    val fa =
      for {
        _ <- taskInError.attempt
        _ = unsafeState() shouldBe CircuitBreaker.Closed(1)
        _ <- taskInError.attempt
        _ = unsafeState() should matchPattern {
          case CircuitBreaker.Open(_, t) if t == 100.millis =>
        }
        res1 <- taskInError.attempt
        _ = res1 should matchPattern {
          case Left(_: CircuitBreaker.RejectedExecution) =>
        }
        _ <- timer.sleep(150.millis)
        res2 <- taskInError.attempt
        _ = res2 should matchPattern {
          case Left(_: RuntimeException) =>
        }
        res3 <- taskInError.attempt
        _ = res3 should matchPattern {
          case Left(_: CircuitBreaker.RejectedExecution) =>
        }
      } yield Succeeded

    fa.unsafeToFuture()
  }


  test("validate parameters") {
    intercept[IllegalArgumentException] {
      // Positive maxFailures
      CircuitBreaker.of[IO](
        maxFailures = -1,
        resetTimeout = 1.minute
      ).unsafeRunSync()
    }

    intercept[IllegalArgumentException] {
      // Strictly positive resetTimeout
      CircuitBreaker.of[IO](
        maxFailures = 2,
        resetTimeout = -1.minute
      ).unsafeRunSync()
    }

    intercept[IllegalArgumentException] {
      // exponentialBackoffFactor >= 1
      CircuitBreaker.of[IO](
        maxFailures = 2,
        resetTimeout = 1.minute,
        exponentialBackoffFactor = 0.5
      ).unsafeRunSync()
    }

    intercept[IllegalArgumentException] {
      // Strictly positive maxResetTimeout
      CircuitBreaker.of[IO](
        maxFailures = 2,
        resetTimeout = 1.minute,
        exponentialBackoffFactor = 2,
        maxResetTimeout = Duration.Zero
      ).unsafeRunSync()
    }

    Future(Succeeded)
  }

  test("Validate onClosed is called when closing from longRunning openOnFail"){
    sealed trait TestFailure extends Throwable
    case object DidNotOpenOrClose extends TestFailure
    case object DidNotOpen extends TestFailure
    case object DidNotClose extends TestFailure
    val test = for {
      cb1 <- CircuitBreaker.of[IO](
        maxFailures = 1,
        resetTimeout = 1.minute,
        exponentialBackoffFactor = 1,
        maxResetTimeout = 1.minute
      )
      opened <- Ref[IO].of(false)
      closed <- Ref[IO].of(false)
      cb = cb1.doOnOpen(opened.set(true)).doOnClosed(closed.set(true))
      dummy = new RuntimeException("dummy")
      taskInError = cb.protect(IO[Int](throw dummy))
      wait <- Deferred[IO, Unit]
      completed <- Deferred[IO, Unit]
      _ <- (cb.protect(wait.get) >> completed.complete(())).start // Will reset when wait completes
      _ <- taskInError.attempt
      didOpen <- opened.get
      _ <- wait.complete(())
      _ <- completed.get
      didClose <- closed.get
      out <- {
        if (didOpen && didClose){
          Succeeded.pure[IO]
        } else if (!didOpen && !didClose) {
          IO.raiseError(DidNotOpenOrClose)
        } else if (!didOpen) {
          IO.raiseError(DidNotOpen)
        } else IO.raiseError(DidNotClose)
      }
    } yield out

    test.unsafeToFuture()
  }

  test("Handles cancel correctly when half-open") {
    val cb = CircuitBreaker
      .of[IO](
        maxFailures = 1,
        resetTimeout = 200.millis,
        exponentialBackoffFactor = 1,
        maxResetTimeout = 1.second
      )
      .unsafeRunSync()

    def unsafeState() = cb.state.unsafeRunSync()

    val test = for {
      _ <- cb.protect(IO.raiseError(new Exception("boom!"))).attempt.void
      _ <- IO.sleep(200.millis)
      fiberStarted <- Deferred[IO, Unit]
      fiber <- cb
        .protect[Unit](fiberStarted.complete(()) >> IO.never)
        .start
      _ <- fiberStarted.get
      _ = unsafeState() shouldBe CircuitBreaker.HalfOpen
      _ <- fiber.cancel
    } yield
      unsafeState() should matchPattern {
        case CircuitBreaker.Open(_, t) if t == 200.millis =>
      }
    test.unsafeToFuture()
  }
}
