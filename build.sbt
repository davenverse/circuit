import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import _root_.io.chrisdavenport.sbtmimaversioncheck.MimaVersionCheckKeys.mimaVersionCheckExcludedVersions

val catsV = "2.6.1"
val catsEffectV = "3.3.0"
val scalaTestV = "3.2.9"

val scala213 = "2.13.6" 
ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := Seq("2.12.14", scala213, "3.0.2")

ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))

lazy val `circuit` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core.jvm, core.js)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "circuit",
    libraryDependencies ++= Seq(
      "org.typelevel"               %%% "cats-core"                  % catsV,
      "org.typelevel"               %%% "cats-effect"                % catsEffectV,
      "org.typelevel" %%% "munit-cats-effect-3" % "1.0.7" % Test,
    ),
  ).jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .enablePlugins(DavenverseMicrositePlugin)
  .dependsOn(core.jvm)
  .settings{
    import microsites._
    Seq(
      micrositeDescription := "Circuit Breaker for Scala",
    )
  }
