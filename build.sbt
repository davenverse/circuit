import com.typesafe.tools.mima.core._

ThisBuild / tlBaseVersion := "0.5" // your current series x.y

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)

ThisBuild / tlCiReleaseBranches := Seq("main")

// true by default, set to false to publish to s01.oss.sonatype.org
ThisBuild / tlSonatypeUseLegacyHost := true

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

val catsV = "2.9.0"
val catsEffectV = "3.4.8"
val scalaTestV = "3.2.9"

val scala213 = "2.13.8"
ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := Seq("2.12.14", scala213, "3.3.0")

ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))

lazy val `circuit` = tlCrossRootProject
  .aggregate(core)


lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "circuit",
    libraryDependencies ++= Seq(
      "org.typelevel"               %%% "cats-core"                  % catsV,
      "org.typelevel"               %%% "cats-effect"                % catsEffectV,
      "org.typelevel" %%% "munit-cats-effect" % "2.0.0-M3" % Test,
    ),
    mimaBinaryIssueFilters := Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("io.chrisdavenport.circuit.CircuitBreaker#SyncCircuitBreaker.this")
    ),
  ).jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  ).nativeSettings(
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.5.1").toMap
  )

lazy val site = project.in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(core.jvm)
