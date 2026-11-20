/** okay-study: a textbook read into a neurosymbolic model — a theory for an exact engine and a reader for its
 * problems, each checked against the book's own answers (specs/study.md) */
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / organization := "io.github.okay-effects"
ThisBuild / homepage := Some(url("https://github.com/okay-effects/okay-study"))
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all", "-Werror")
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / developers := List(Developer("sergey-scherbina", "Sergiy Shcherbyna", "sergey.scherbina@gmail.com",
  url("https://github.com/sergey-scherbina")))
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/okay-effects/okay-study"),
  "scm:git@github.com:okay-effects/okay-study.git"))
ThisBuild / versionScheme := Some("early-semver")
/** RELEASES, ON GITHUB PAGES: `sbt publish` writes into `target/pages-maven/` (RELEASING.md) */
val pagesRepository = settingKey[File]("the Maven repository a release is written to, for GitHub Pages")
ThisBuild / pagesRepository := (ThisBuild / baseDirectory).value / "target" / "pages-maven"
ThisBuild / publishTo := Some(MavenCache("pages", (ThisBuild / pagesRepository).value))
ThisBuild / Compile / packageDoc / publishArtifact := false
ThisBuild / resolvers ++= Seq("okay-datalog" at "https://okay-effects.github.io/okay-datalog/maven",
  "okay-llm" at "https://okay-effects.github.io/okay-llm/maven",
  "okay-scala" at "https://okay-effects.github.io/okay-scala/maven",
  "okay-simple" at "https://okay-effects.github.io/okay-simple/maven")

val munit = "org.scalameta" %% "munit" % "1.2.3" % Test

/** the model as data and its exact run: no language model here — what a phone needs; the JVM and Scala.js
 * (the tests read the books' files: the JVM's) */
lazy val core = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Full).in(file("core"))
  .settings(name := "okay-study",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-datalog" % "0.1.0",
      "io.github.okay-effects" %%% "okay-effects-json-value" % "0.1.1"))
  .jvmSettings(libraryDependencies += munit, Test / fork := true,
    Test / baseDirectory := (ThisBuild / baseDirectory).value)

/** the study: a teacher (the effect `Llm`) writes the model, the book's answers judge it */
lazy val study = (project in file("study")).dependsOn(core.jvm).settings(name := "okay-study-teacher",
  libraryDependencies ++= Seq("io.github.okay-effects" %% "okay-llm" % "0.1.0", munit),
  run / fork := true, run / baseDirectory := (ThisBuild / baseDirectory).value,
  Test / fork := true, Test / baseDirectory := (ThisBuild / baseDirectory).value)

lazy val root = (project in file(".")).aggregate(core.jvm, core.js, study).settings(publish / skip := true)

/** the small reader: a language model on Apple's GPU (mlx-scala) weighs a premiss's readings said in words */
val mlxNativeLib =
  sys.env.get("MLX_SCALA_LIB").map(file).getOrElse(file("../mlx-scala/native-lib/out/lib")).getCanonicalFile
lazy val reader = (project in file("reader")).dependsOn(core.jvm, study).settings(name := "okay-study-reader",
  publish / skip := true, resolvers += "mlx-scala" at "https://sergey-scherbina.github.io/mlx-scala/maven",
  libraryDependencies ++= Seq("io.github.sergey-scherbina" %% "mlx-scala" % "0.1.0",
    "io.github.sergey-scherbina" %% "mlx-scala-llm" % "0.1.0", munit),
  fork := true, run / baseDirectory := (ThisBuild / baseDirectory).value,
  Test / baseDirectory := (ThisBuild / baseDirectory).value,
  javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED", s"-Dokm.lib=$mlxNativeLib/libokm.dylib", "-Xss16m"))
