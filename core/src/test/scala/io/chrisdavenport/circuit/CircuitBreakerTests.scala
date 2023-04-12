/*
 * Initial Implementation by The Monix Project Developers
 * Copyright (c) 2014-2018 Alexandru Nedelcu
 * Copyright (c) 2014-2018 Oleg Pyzhcov
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

import cats.syntax.all._
import scala.concurrent.duration._
import cats.effect._
// import cats.effect.syntax._

// import catalysts.Platform
import munit.CatsEffectSuite

class CircuitBreakerTests extends CatsEffectSuite {
  private val Tries = 10000 //if (Platform.isJvm) 10000 else 5000

  private def mkBreaker() = CircuitBreaker.in[SyncIO, IO](
    maxFailures = 5,
    resetTimeout = 1.minute
  ).unsafeRunSync()


  test("should work for successful async IOs") {
    val circuitBreaker = mkBreaker()

    var effect = 0
    val task = circuitBreaker.protect(IO {
      effect += 1
    })

    List.fill(Tries)(task).sequence_.map { _ =>
      assertEquals(effect, Tries)
    }
  }

  test("should work for successful immediate tasks") {
    val circuitBreaker = mkBreaker()

    var effect = 0
    val task = circuitBreaker.protect(IO {
      effect += 1
    })

    List.fill(Tries)(task).sequence_.map { _ =>
      assertEquals(effect, Tries)
    }
  }

  test("should be stack safe for successful async tasks (flatMap)") {
    val circuitBreaker = mkBreaker()

    def loop(n: Int, acc: Int): IO[Int] = {
      if (n > 0)
        circuitBreaker.protect(IO(acc+1))
          .flatMap(s => loop(n-1, s))
      else
        IO.pure(acc)
    }

    loop(Tries, 0).map { value =>
      assertEquals(value, Tries)
    }
  }

  test("should be stack safe for successful async tasks (suspend)") {
    val circuitBreaker = mkBreaker()

    def loop(n: Int, acc: Int): IO[Int] =
      IO.defer {
        if (n > 0)
          circuitBreaker.protect(loop(n-1, acc+1))
        else
          IO.pure(acc)
      }

    loop(Tries, 0).map { value =>
      assertEquals(value, Tries)
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

    loop(Tries, 0).map { value =>
      assertEquals(value, Tries)
    }
  }

  test("should be stack safe for successful immediate tasks (suspend)") {
    val circuitBreaker = mkBreaker()

    def loop(n: Int, acc: Int): IO[Int] =
      IO.defer {
        if (n > 0)
          circuitBreaker.protect(loop(n-1, acc+1))
        else
          IO.pure(acc)
      }

    loop(Tries, 0).map { value =>
      assertEquals(value, Tries)
    }
  }

  test("complete workflow with failures and exponential backoff") {
    var openedCount = 0
    var closedCount = 0
    var halfOpenCount = 0
    var rejectedCount = 0

    val circuitBreaker = {
      val cb = CircuitBreaker.in[SyncIO, IO](
        maxFailures = 5,
        resetTimeout = 500.millis,
        backoff = Backoff.exponential,
        maxResetTimeout = 1.second
      ).unsafeRunSync()

      cb.doOnOpen(IO { openedCount += 1})
        .doOnClosed(IO { closedCount += 1 })
        .doOnHalfOpen(IO { halfOpenCount += 1 })
        .doOnRejected(IO { rejectedCount += 1 })
    }

    // def unsafeState() = circuitBreaker.state.unsafeRunSync()

    val dummy = new RuntimeException("dummy")
    val taskInError = circuitBreaker.protect(IO[Int](throw dummy))
    val taskSuccess = circuitBreaker.protect(IO { 1 })
    val fa =
      for {
        _ <- taskInError.attempt
        _ <- taskInError.attempt
        _ <- circuitBreaker.state.map(assertEquals(_, CircuitBreaker.Closed(2)))
        // A successful value should reset the counter
        _ <- taskSuccess
        _ <- circuitBreaker.state.map(assertEquals(_, CircuitBreaker.Closed(0)))

        _ <- taskInError.attempt.replicateA(5)
        _ <- circuitBreaker.state.map {
          case CircuitBreaker.Open(_, t)=> assertEquals(t, 500.millis)
          case _ => assert(false)
        }
        _ <- taskSuccess.attempt.map{
          case Left(_: CircuitBreaker.RejectedExecution) => assert(true)
          case _ => assert(false)
        }
        // Should still fail-fast
        _ <- taskSuccess.attempt.map{
          case Left(_: CircuitBreaker.RejectedExecution) => assert(true)
          case _ => assert(false)
        }

        _ <- IO.sleep(1000.millis)

        // Testing half-open state
        d <- Deferred[IO, Unit]
        fiber <- circuitBreaker.protect(d.get).start
        _ <- IO.sleep(2.seconds)
        _ <- circuitBreaker.state.map(assertEquals(_, CircuitBreaker.HalfOpen))

        // Should reject other tasks

        _ <- taskSuccess.attempt.map{
          case Left(_: CircuitBreaker.RejectedExecution) => assert(true)
          case _ => assert(false)
        }

        _ <- d.complete(())
        _ <- fiber.join

        // Should re-open on success
        _ <- circuitBreaker.state.map(assertEquals(_, CircuitBreaker.Closed(0)))
      } yield assertEquals( (openedCount, closedCount, halfOpenCount, rejectedCount), (1, 1, 1, 3))

    fa
  }

  test("keep rejecting with consecutive call errors") {

    val circuitBreaker =
      CircuitBreaker.in[SyncIO, IO](
        maxFailures = 2,
        resetTimeout = 100.millis,
        backoff = Backoff.constant(100.millis),
        maxResetTimeout = 100.millis
      ).unsafeRunSync()

    val dummy = new RuntimeException("dummy")
    val taskInError = circuitBreaker.protect(IO[Int](throw dummy))
    val fa =
      for {
        _ <- taskInError.attempt
        _ <- circuitBreaker.state.map(assertEquals(_, CircuitBreaker.Closed(1)))
        _ <- taskInError.attempt
        _ <- circuitBreaker.state.map{
          case CircuitBreaker.Open(_, t) => assertEquals(t, 100.millis)
          case _ => assert(false)
        }
        _ <- taskInError.attempt.map{
          case Left(_: CircuitBreaker.RejectedExecution) => assert(true)
          case _ => assert(false)
        }
        _ <- Temporal[IO].sleep(150.millis)
        _ <- taskInError.attempt.map{
          case Left(_: RuntimeException) => assert(true)
          case _ => assert(false)
        }
        _ <- taskInError.attempt.map{
          case Left(_: CircuitBreaker.RejectedExecution) => assert(true)
          case _ => assert(false)
        }
      } yield ()

    fa
  }


  test("validate parameters") {
    intercept[IllegalArgumentException] {
      // Positive maxFailures
      CircuitBreaker.in[SyncIO, IO](
        maxFailures = -1,
        resetTimeout = 1.minute
      ).unsafeRunSync()
    }

    intercept[IllegalArgumentException] {
      // Strictly positive resetTimeout
      CircuitBreaker.in[SyncIO, IO](
        maxFailures = 2,
        resetTimeout = -1.minute
      ).unsafeRunSync()
    }

    intercept[IllegalArgumentException] {
      // Strictly positive maxResetTimeout
      CircuitBreaker.in[SyncIO, IO](
        maxFailures = 2,
        resetTimeout = 1.minute,
        backoff = Backoff.exponential,
        maxResetTimeout = Duration.Zero
      ).unsafeRunSync()
    }

    assert(true)
  }

  test("Handles cancel correctly when half-open") {
    val cb = CircuitBreaker
      .in[SyncIO, IO](
        maxFailures = 1,
        resetTimeout = 200.millis,
        backoff = Backoff.constant(200.millis),
        maxResetTimeout = 1.second
      )
      .unsafeRunSync()

    val test = for {
      _ <- cb.protect(IO.raiseError(new Exception("boom!"))).attempt.void
      _ <- IO.sleep(200.millis)
      fiberStarted <- Deferred[IO, Unit]
      fiber <- cb
        .protect[Unit](fiberStarted.complete(()) >> IO.never)
        .start
      _ <- fiberStarted.get
      _ <- cb.state.map(assertEquals(_, CircuitBreaker.HalfOpen))
      _ <- fiber.cancel
      _ <- cb.state.map{
        case CircuitBreaker.Open(_, t) => assertEquals(t, 200.millis)
        case _ => assert(false)
      }
    } yield ()
    test
  }

  test("Validate onClosed is called when closing from longRunning openOnFail"){
    val test = for {
      cb1 <- CircuitBreaker.of[IO](
        maxFailures = 1,
        resetTimeout = 1.minute,
        backoff = Backoff.constant(1.minute),
        maxResetTimeout = 1.minute
      )
      opened <- Ref[IO].of(false)
      closed <- Ref[IO].of(false)
      cb = cb1.doOnOpen(opened.set(true)).doOnClosed(closed.set(true))
      dummy = new RuntimeException("dummy")
      taskInError = cb.protect(IO[Int](throw dummy))
      started <- Deferred[IO, Unit]
      wait <- Deferred[IO, Unit]
      completed <- Deferred[IO, Unit]
      _ <- (started.complete(()) >> cb.protect(wait.get) >> completed.complete(())).start // Will reset when wait completes
      _ <- started.get
      _ <- IO.sleep(100.millis)
      _ <- taskInError.attempt
      _ <- opened.get.map(assertEquals(_, true))
      _ <- wait.complete(())
      _ <- completed.get
      didClose <- closed.get
    } yield assertEquals(didClose, true)

    test
  }

  test("should only count allowed exceptions") {
    case class MyException(foo: String) extends Throwable

    for {
      circuitBreaker <- CircuitBreaker.default[IO](maxFailures = 1, resetTimeout = 10.seconds).withExceptionFilter(exceptionFilter = !_.isInstanceOf[MyException]).build
      action = circuitBreaker.protect(IO.raiseError(MyException("Boom!"))).attempt
      _ <- action >> action >> action >> action
      _ <- circuitBreaker.state.map {
        case _: CircuitBreaker.Closed => assert(true)
        case _ => assert(false)
      }
      badAction = circuitBreaker.protect(IO.raiseError(new RuntimeException("Boom!"))).attempt
      _ <- badAction >> badAction >> badAction >> badAction
      _ <- circuitBreaker.state.map {
        case _: CircuitBreaker.Open => assert(true)
        case _ => assert(false)
      }
    } yield ()
  }
}
