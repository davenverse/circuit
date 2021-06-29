import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val scala213V = "2.13.2"
val scala212V = "2.12.10"

val catsV = "2.2.0"
val catsEffectV = "2.2.0"
val scalaTestV = "3.2.2"

val kindProjectorV = "0.11.3"
val betterMonadicForV = "0.3.1"

ThisBuild / crossScalaVersions := Seq(scala212V, scala213V)

ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))
// ThisBuild / organization := "io.chrisdavenport"
// ThisBuild / developers := List(Developer("ChristopherDavenport", "Christopher Davenport", "chris@christopherdavenport.tech", url("https://github.com/ChristopherDavenport")))

lazy val `circuit` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = project.in(file("core"))
  .settings(
    name := "circuit",
    addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorV cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForV),
    libraryDependencies ++= Seq(
      "org.typelevel"               %% "cats-core"                  % catsV,
      "org.typelevel"               %% "cats-effect"                % catsEffectV,
      "org.scalatest"               %% "scalatest"                  % scalaTestV % Test
    )
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(DavenverseSitePlugin)
  .dependsOn(core)
  .settings{
    import microsites._
    Seq(
      micrositeDescription := "Circuit Breaker for Scala",
    )
  }
