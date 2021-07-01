import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import _root_.io.chrisdavenport.sbtmimaversioncheck.MimaVersionCheckKeys.mimaVersionCheckExcludedVersions

val catsV = "2.6.1"
val catsEffectV = "3.1.1"
val scalaTestV = "3.2.9"

val scala213 = "2.13.6" 
ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := Seq("2.12.14", scala213, "3.0.0")

ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))

lazy val `circuit` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .disablePlugins(MimaPlugin)
  .settings(
    name := "circuit",
    libraryDependencies ++= Seq(
      "org.typelevel"               %% "cats-core"                  % catsV,
      "org.typelevel"               %% "cats-effect"                % catsEffectV,
      "org.scalatest"               %% "scalatest"                  % scalaTestV % Test
    ),
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(DavenverseMicrositePlugin)
  .dependsOn(core)
  .settings{
    import microsites._
    Seq(
      micrositeDescription := "Circuit Breaker for Scala",
    )
  }
