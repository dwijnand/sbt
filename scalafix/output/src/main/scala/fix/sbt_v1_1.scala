package fix

import sbt._, Keys._

object sbt_v1_1_Test {
  // a value of type Project to test with
  // important to test because scoping is in terms of Reference, not Project
  // so this tests the interaction with the Project => ProjectReference implicit
  val projA = Project("projA", file("."))


  // test in(Scope)
  Global / cancelable
  Global / skip
  Global / run

  // test non-infix syntax
  Global / cancelable

  // test parentheses are kept
  (Global / cancelable).key
  Def task ((Global / cancelable).value)
  cancelable := (Global / cancelable).value
  crossScalaVersions := {
    val log = sLog.value
    val manifest = baseDirectory.value / ".travis.yml"
    val default = (Global / crossScalaVersions).value
    default
  }

  // test Scope being defined multiple times
  Global / cancelable
  Global / skip
  Global / run

  // test the interaction between Scope and another axis (Reference here) being defined
  Global / cancelable
  ThisBuild / Zero / Zero / cancelable
  ThisBuild / cancelable
  // additionally test with custom Scope values
  cancelable in Global.copy(task = This) in ThisBuild
  cancelable in ThisScope.copy(task = Zero) in ThisBuild


  // test in(Reference)
  ThisBuild / scalaVersion
  projA / scalaVersion

  // test Reference being defined multiple times
  projA / scalaVersion
  ThisBuild / scalaVersion


  // test in(Scoped)
  console / scalacOptions

  // define a custom key that's not defined in Keys and is "pre-scoped"
  // to test that ".key" is used
  val fooKey = ThisBuild / taskKey[Int]("")
  fooKey.key / scalacOptions

  // test scoped being defined multiple times
  compile / scalacOptions
  console / scalacOptions


  // test in(ConfigKey)
  Compile / scalacOptions

  // test configuration being defined multiple times
  Test / scalacOptions
  Compile / scalacOptions


  // test in(ConfigKey, Scoped)
  Compile / console / scalacOptions
  Compile / console / scalacOptions
  Compile / console / scalacOptions

  // test configuration or scoped being defined multiple times (1/3)
  Compile / console / scalacOptions
  Test / console / scalacOptions
  Compile / console / scalacOptions
  Compile / compile / scalacOptions

  // test configuration or scoped being defined multiple times (2/3)
  Compile / console / scalacOptions
  Test / console / scalacOptions
  Compile / console / scalacOptions
  Compile / compile / scalacOptions

  // test configuration or scoped being defined multiple times (3/3)
  Compile / console / scalacOptions
  Test / console / scalacOptions
  Compile / console / scalacOptions
  Compile / compile / scalacOptions


  // test in(Reference, ConfigKey)
  projA / Compile / scalacOptions
  projA / Compile / scalacOptions
  projA / Compile / scalacOptions

  // test reference or configuration being defined multiple times (1/3)
  projA / Compile / scalacOptions
  ThisBuild / Compile / scalacOptions
  projA / Compile / scalacOptions
  projA / Test / scalacOptions

  // test reference or configuration being defined multiple times (2/3)
  projA / Compile / scalacOptions
  ThisBuild / Compile / scalacOptions
  projA / Compile / scalacOptions
  projA / Test / scalacOptions

  // test reference or configuration being defined multiple times (3/3)
  projA / Compile / scalacOptions
  ThisBuild / Compile / scalacOptions
  projA / Compile / scalacOptions
  projA / Test / scalacOptions


  // test in(Reference, Scoped)
  projA / console / scalacOptions
  projA / console / scalacOptions
  projA / console / scalacOptions

  // test reference or scoped being defined multiple times (1/3)
  projA / console / scalacOptions
  ThisBuild / console / scalacOptions
  projA / console / scalacOptions
  projA / compile / scalacOptions

  // test reference or scoped being defined multiple times (2/3)
  projA / console / scalacOptions
  ThisBuild / console / scalacOptions
  projA / console / scalacOptions
  projA / compile / scalacOptions

  // test reference or scoped being defined multiple times (3/3)
  projA / console / scalacOptions
  ThisBuild / console / scalacOptions
  projA / console / scalacOptions
  projA / compile / scalacOptions


  // test in(Reference, ConfigKey, Scoped)
  projA / Compile / console / scalacOptions

  // test all permutations of defining the 3 axes (1/4)
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions

  // test all permutations of defining the 3 axes (2/4)
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions

  // test all permutations of defining the 3 axes (3/4)
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions

  // test all permutations of defining the 3 axes (4/4)
  projA / Compile / console / scalacOptions
  projA / Compile / console / scalacOptions


  // test in(ScopeAxis[Reference], ScopeAxis[ConfigKey], ScopeAxis[AttributeKey[_]])
  Select(projA: Reference) / Select(Compile: ConfigKey) / Select(console.key) / scalacOptions
}
