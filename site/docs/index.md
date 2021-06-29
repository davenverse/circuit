---
layout: home

---

# circuit - Circuit Breaker for Scala [![Build Status](https://travis-ci.com/ChristopherDavenport/circuit.svg?branch=master)](https://travis-ci.com/ChristopherDavenport/circuit) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/circuit_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/circuit_2.12)

The `CircuitBreaker` is used to provide stability and prevent cascading failures in distributed systems.

## Quick Start

To use circuit in an existing SBT project with Scala 2.11 or a later version, add the following dependencies to your
`build.sbt` depending on your needs:

```scala
libraryDependencies ++= Seq(
  "io.chrisdavenport" %% "circuit" % "<version>"
)
```

## Purpose

As an example, we have a web application interacting with a remote
third party web service. Let's say the third party has oversold
their capacity and their database melts down under load. Assume
that the database fails in such a way that it takes a very long
time to hand back an error to the third party web service. This in
turn makes calls fail after a long period of time.  Back to our
web application, the users have noticed that their form
submissions take much longer seeming to hang. Well the users do
what they know to do which is use the refresh button, adding more
requests to their already running requests.  This eventually
causes the failure of the web application due to resource
exhaustion. This will affect all users, even those who are not
using functionality dependent on this third party web service.

Introducing circuit breakers on the web service call would cause
the requests to begin to fail-fast, letting the user know that
something is wrong and that they need not refresh their
request. This also confines the failure behavior to only those
users that are using functionality dependent on the third party,
other users are no longer affected as there is no resource
exhaustion. Circuit breakers can also allow savvy developers to
mark portions of the site that use the functionality unavailable,
or perhaps show some cached content as appropriate while the
breaker is open.

## How It Works

The circuit breaker models a concurrent state machine that
can be in any of these 3 states:

**Closed**: During normal operations or when the `CircuitBreaker` starts

- Exceptions increment the `failures` counter

- Successes reset the failure count to zero

- When the `failures` counter reaches the `maxFailures` count,the breaker is tripped into `Open` state

**Open**: The circuit breaker rejects all tasks with an RejectedExecution

- all tasks fail fast with `RejectedExecution`

- after the configured `resetTimeout`, the circuit breaker enters a `HalfOpen` state, allowing one task to go through for testing the connection

**HalfOpen**: The circuit breaker has already allowed a task to go through, as a reset attempt, in order to test the connection

- The first task when `Open` has expired is allowed through without failing fast, just before the circuit breaker is evolved into the `HalfOpen` state

- All tasks attempted in `HalfOpen` fail-fast with an exception just as in `Open` state

- If that task attempt succeeds, the breaker is reset back to the `Closed` state, with the `resetTimeout` and the `failures` count also reset to initial values

- If the first call fails, the breaker is tripped again into the `Open` state (the `resetTimeout` is multiplied by the exponential backoff factor)

## Usage

First some imports.

```tut:silent
import cats.effect._
import io.chrisdavenport.circuit.CircuitBreaker
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.global
implicit val T = IO.timer(global)
```

Then we construct the circuit breaker.

```scala mdoc
val circuitBreaker = CircuitBreaker.of[IO](
  maxFailures = 5,
  resetTimeout = 10.seconds
)

//...
val problematic = IO {
  val nr = scala.util.Random.nextInt()
  if (nr % 2 == 0) nr else
    throw new RuntimeException("dummy")
}

val task = circuitBreaker.flatMap(
  _.protect(problematic) // Circuit Breaker Shared State Location
)
```

When attempting to close the circuit breaker and resume normal operations, we can also apply an exponential backoff for repeated failed attempts, like so:

```scala mdoc
val exponential = CircuitBreaker.of[IO](
  maxFailures = 5,
  resetTimeout = 10.seconds,
  exponentialBackoffFactor = 2,
  maxResetTimeout = 10.minutes
)
```

In this sample we attempt to reconnect after 10 seconds, then after
20, 40 and so on, a delay that keeps increasing up to a configurable
maximum of 10 minutes.

## Credits

This data type was inspired by the availability of [Akka's Circuit Breaker](http://doc.akka.io/docs/akka/current/common/circuitbreaker.html) and ported to cats-effect from [Monix](https://monix.io) and when its merger halted there, it was moved to [circuit](https://github.com/ChristopherDavenport/circuit)