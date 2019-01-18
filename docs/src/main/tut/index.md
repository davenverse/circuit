---
layout: home

---

# circuit - Circuit Breaker for Scala [![Build Status](https://travis-ci.com/ChristopherDavenport/circuit.svg?branch=master)](https://travis-ci.com/ChristopherDavenport/circuit) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/circuit_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.chrisdavenport/circuit_2.12)

## Quick Start

To use circuit in an existing SBT project with Scala 2.11 or a later version, add the following dependencies to your
`build.sbt` depending on your needs:

```scala
libraryDependencies ++= Seq(
  "io.chrisdavenport" %% "circuit" % "<version>"
)
```