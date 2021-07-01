import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import _root_.io.chrisdavenport.sbtmimaversioncheck.MimaVersionCheckKeys.mimaVersionCheckExcludedVersions

val catsV = "2.6.1"
val catsEffectV = "2.5.1"
val scalaTestV = "3.2.9"

ThisBuild / crossScalaVersions := Seq("2.12.14", "2.13.6", "3.0.0")

ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))

lazy val `circuit` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "circuit",
    libraryDependencies ++= Seq(
      "org.typelevel"               %% "cats-core"                  % catsV,
      "org.typelevel"               %% "cats-effect"                % catsEffectV,
      "org.scalatest"               %% "scalatest"                  % scalaTestV % Test
    ),
    mimaVersionCheckExcludedVersions := Set(
      "0.4.0",
      "0.4.2"
    ) ++ {
      if (isDotty.value) Set("0.4.1") else Set()
    }
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
