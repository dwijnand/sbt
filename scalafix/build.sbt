// Necessary because scalafix is also the name of the key, and its in scope.
import _root_.scalafix.Versions.{ scala212, version => scalafixVersion }

// Use a scala version supported by scalafix.
scalaVersion in ThisBuild := scala212

val rules = project settings (
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % scalafixVersion
)

val input = project settings (
  scalafixSourceroot := (sourceDirectory in Compile).value,
  libraryDependencies += "org.scala-sbt" % "sbt" % "1.1.0-M1"
)

val output = project settings (
  libraryDependencies += "org.scala-sbt" % "sbt" % "1.1.0-M1"
  // TODO: Switch to dependsOn(ProjectRef(file(".").getParentFile, "sbtProj"))
)

val tests = project dependsOn (input, rules) enablePlugins BuildInfoPlugin settings (
  libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % scalafixVersion % Test cross CrossVersion.full,
  buildInfoPackage := "fix",
  buildInfoKeys := Seq[BuildInfoKey](
        "inputSourceroot" -> (sourceDirectory in  input in Compile).value,
       "outputSourceroot" -> (sourceDirectory in output in Compile).value,
    "inputClassdirectory" -> ( classDirectory in  input in Compile).value
  )
)
