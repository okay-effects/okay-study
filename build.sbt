/** okay-study: a textbook read into a neurosymbolic model — a theory for an exact engine and a reader for its
 * problems, each checked against the book's own answers (specs/study.md) */
ThisBuild / organization := "io.github.okay-effects"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all", "-Werror")
ThisBuild / resolvers ++= Seq("okay-datalog" at "https://okay-effects.github.io/okay-datalog/maven",
  "okay-llm" at "https://okay-effects.github.io/okay-llm/maven",
  "okay-scala" at "https://okay-effects.github.io/okay-scala/maven",
  "okay-simple" at "https://okay-effects.github.io/okay-simple/maven")

val munit = "org.scalameta" %% "munit" % "1.2.3" % Test

/** the model as data and its exact run: no language model here — what a phone needs */
lazy val core = (project in file("core")).settings(name := "okay-study",
  libraryDependencies ++= Seq("io.github.okay-effects" %% "okay-datalog" % "0.1.0",
    "io.github.okay-effects" %% "okay-effects-json-value" % "0.1.1", munit),
  Test / fork := true, Test / baseDirectory := (ThisBuild / baseDirectory).value)

/** the study: a teacher (the effect `Llm`) writes the model, the book's answers judge it */
lazy val study = (project in file("study")).dependsOn(core).settings(name := "okay-study-teacher",
  libraryDependencies ++= Seq("io.github.okay-effects" %% "okay-llm" % "0.1.0", munit),
  run / fork := true, run / baseDirectory := (ThisBuild / baseDirectory).value,
  Test / fork := true, Test / baseDirectory := (ThisBuild / baseDirectory).value)

lazy val root = (project in file(".")).aggregate(core, study).settings(publish / skip := true)
