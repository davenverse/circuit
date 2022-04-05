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
