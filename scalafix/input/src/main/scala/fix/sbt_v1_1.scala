/*
rule = "class:fix.sbt_v1_1"
*/
package fix

import sbt._, Keys._

object sbt_v1_1_Test {
  // a value of type Project to test with
  // important to test because scoping is in terms of Reference, not Project
  // so this tests the interaction with the Project => ProjectReference implicit
  val projA = Project("projA", file("."))


  // test in(Scope)
  cancelable in Global
  skip in Global
  run in Global

  // test non-infix syntax
  cancelable.in(Global)

  // test Scope being defined multiple times
  cancelable in ThisScope in Global
  skip in ThisScope in Global
  run in ThisScope in Global

  // test the interaction between Scope and another axis (Reference here) being defined
  cancelable in ThisBuild in Global
  cancelable in Global in ThisBuild
  cancelable in ThisScope in ThisBuild
  // additionally test with custom Scope values
  cancelable in Global.copy(task = This) in ThisBuild
  cancelable in ThisScope.copy(task = Zero) in ThisBuild


  // test in(Reference)
  scalaVersion in ThisBuild
  scalaVersion in projA

  // test Reference being defined multiple times
  scalaVersion in ThisBuild in projA
  scalaVersion in projA in ThisBuild


  // test in(Scoped)
  scalacOptions in console

  // define a custom key that's not defined in Keys and is "pre-scoped"
  // to test that ".key" is used
  val fooKey = taskKey[Int]("") in ThisBuild
  scalacOptions in fooKey

  // test scoped being defined multiple times
  scalacOptions in console in compile
  scalacOptions in compile in console


  // test in(ConfigKey)
  scalacOptions in Compile

  // test configuration being defined multiple times
  scalacOptions in Compile in Test
  scalacOptions in Test in Compile


  // test in(ConfigKey, Scoped)
  scalacOptions in (Compile, console)
  scalacOptions in Compile in console
  scalacOptions in console in Compile

  // test configuration or scoped being defined multiple times (1/3)
  scalacOptions in Test in (Compile, console)
  scalacOptions in (Compile, console) in Test
  scalacOptions in compile in (Compile, console)
  scalacOptions in (Compile, console) in compile

  // test configuration or scoped being defined multiple times (2/3)
  scalacOptions in Test in Compile in console
  scalacOptions in Compile in console in Test
  scalacOptions in compile in Compile in console
  scalacOptions in Compile in console in compile

  // test configuration or scoped being defined multiple times (3/3)
  scalacOptions in Test in console in Compile
  scalacOptions in console in Compile in Test
  scalacOptions in compile in console in Compile
  scalacOptions in console in Compile in compile


  // test in(Reference, ConfigKey)
  scalacOptions in (projA, Compile)
  scalacOptions in projA in Compile
  scalacOptions in Compile in projA

  // test reference or configuration being defined multiple times (1/3)
  scalacOptions in ThisBuild in (projA, Compile)
  scalacOptions in (projA, Compile) in ThisBuild
  scalacOptions in Test in (projA, Compile)
  scalacOptions in (projA, Compile) in Test

  // test reference or configuration being defined multiple times (2/3)
  scalacOptions in ThisBuild in projA in Compile
  scalacOptions in projA in Compile in ThisBuild
  scalacOptions in Test in projA in Compile
  scalacOptions in projA in Compile in Test

  // test reference or configuration being defined multiple times (3/3)
  scalacOptions in ThisBuild in Compile in projA
  scalacOptions in Compile in projA in ThisBuild
  scalacOptions in Test in Compile in projA
  scalacOptions in Compile in projA in Test


  // test in(Reference, Scoped)
  scalacOptions in (projA, console)
  scalacOptions in projA in console
  scalacOptions in console in projA

  // test reference or scoped being defined multiple times (1/3)
  scalacOptions in ThisBuild in (projA, console)
  scalacOptions in (projA, console) in ThisBuild
  scalacOptions in compile in (projA, console)
  scalacOptions in (projA, console) in compile

  // test reference or scoped being defined multiple times (2/3)
  scalacOptions in ThisBuild in projA in console
  scalacOptions in projA in console in ThisBuild
  scalacOptions in compile in projA in console
  scalacOptions in projA in console in compile

  // test reference or scoped being defined multiple times (3/3)
  scalacOptions in ThisBuild in console in projA
  scalacOptions in console in projA in ThisBuild
  scalacOptions in compile in console in projA
  scalacOptions in console in projA in compile


  // test in(Reference, ConfigKey, Scoped)
  scalacOptions in (projA, Compile, console)

  // test all permutations of defining the 3 axes (1/4)
  scalacOptions in projA in Compile in console
  scalacOptions in projA in console in Compile
  scalacOptions in Compile in projA in console
  scalacOptions in Compile in console in projA
  scalacOptions in console in projA in Compile
  scalacOptions in console in Compile in projA

  // test all permutations of defining the 3 axes (2/4)
  scalacOptions in (Compile, console) in projA
  scalacOptions in projA in (Compile, console)

  // test all permutations of defining the 3 axes (3/4)
  scalacOptions in (projA, console) in Compile
  scalacOptions in Compile in (projA, console)

  // test all permutations of defining the 3 axes (4/4)
  scalacOptions in (projA, Compile) in console
  scalacOptions in console in (projA, Compile)


  // test in(ScopeAxis[Reference], ScopeAxis[ConfigKey], ScopeAxis[AttributeKey[_]])
  scalacOptions in (Select(projA: Reference), Select(Compile: ConfigKey), Select(console.key))
}
