import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val scala213V = "2.13.1"
val scala212V = "2.12.10"

val catsV = "2.0.0"
val catsEffectV = "2.0.0"
val scalaTestV = "3.2.0-M1"

val kindProjectorV = "0.11.0"
val betterMonadicForV = "0.3.1"

lazy val `circuit` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .settings(publish / skip := true)
  .aggregate(core)

lazy val core = project.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "circuit"
  )

lazy val docs = project.in(file("docs"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(TutPlugin)
  .dependsOn(core)
  .settings(publish / skip := true)
  .settings(commonSettings)
  .settings(crossScalaVersions := Seq(scala212V))
  .settings{
    import microsites._
    Seq(
      micrositeName := "circuit",
      micrositeDescription := "Circuit Breaker for Scala",
      micrositeAuthor := "Christopher Davenport",
      micrositeGithubOwner := "ChristopherDavenport",
      micrositeGithubRepo := "circuit",
      micrositeBaseUrl := "/circuit",
      micrositeDocumentationUrl := "https://www.javadoc.io/doc/io.chrisdavenport/circuit_2.12",
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      fork in tut := true,
      scalacOptions in Tut --= Seq(
        "-Xfatal-warnings",
        "-Ywarn-unused-import",
        "-Ywarn-numeric-widen",
        "-Ywarn-dead-code",
        "-Ywarn-unused:imports",
        "-Xlint:-missing-interpolator,_"
      ),
      libraryDependencies += "com.47deg" %% "github4s" % "0.20.1",
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFiles := Map(
          file("CODE_OF_CONDUCT.md")  -> ExtraMdFileConfig("code-of-conduct.md",   "page", Map("title" -> "code of conduct",   "section" -> "code of conduct",   "position" -> "101")),
          file("LICENSE")             -> ExtraMdFileConfig("license.md",   "page", Map("title" -> "license",   "section" -> "license",   "position" -> "102"))
      )
    )
  }

// General Settings
lazy val commonSettings = Seq(

  scalaVersion := scala213V,
  crossScalaVersions := Seq(scalaVersion.value, scala212V),

  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForV),
  libraryDependencies ++= Seq(
    "org.typelevel"               %% "cats-core"                  % catsV,
    "org.typelevel"               %% "cats-effect"                % catsEffectV,
    "org.scalatest"               %% "scalatest"                  % scalaTestV % Test
  )
)

inThisBuild(List(
  organization := "io.chrisdavenport",
  developers := List(
    Developer("ChristopherDavenport", "Christopher Davenport", "chris@christopherdavenport.tech", url("https://github.com/ChristopherDavenport"))
  ),
  homepage := Some(url("https://github.com/ChristopherDavenport/circuit")),
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  pomIncludeRepository := { _ => false },
  scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url", "https://github.com/ChristopherDavenport/circuit/blob/v" + version.value + "€{FILE_PATH}.scala"
  ),
))
