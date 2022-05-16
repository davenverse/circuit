# circuit - Circuit Breaker for Scala [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/circuit_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/circuit_2.13)

The `CircuitBreaker` is used to provide stability and prevent cascading failures in distributed systems.

## [Head on over to the microsite](https://davenverse.github.io/circuit)

## Quick Start

To use circuit in an existing SBT project with Scala 2.11 or a later version, add the following dependencies to your
`build.sbt` depending on your needs:

```scala
libraryDependencies ++= Seq(
  "io.chrisdavenport" %% "circuit" % "<version>"
)
```

## Credits

This data type was inspired by the availability of [Akka's Circuit Breaker](http://doc.akka.io/docs/akka/current/common/circuitbreaker.html) and ported to cats-effect from [Monix](https://monix.io) and when its merger halted there, it was moved to [circuit](https://github.com/ChristopherDavenport/circuit). The initial implementation and port by Alexandru Nedelcu and Oleg Pyzhcov was what enabled this ref based version to exist.
