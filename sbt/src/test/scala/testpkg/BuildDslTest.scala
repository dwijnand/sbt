/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt._

object BuildDslTest {
  val bippy = project
  val dingo = project
  val roger = project

  val foo = taskKey[File]("")

  val projects = Seq[Project](dingo, roger)
  val projectRefs = Seq[ProjectReference](dingo, roger)

  bippy.dependsOn(projects: _*)
  Def.task(()).dependsOn(projects.map(foo in _): _*)

  bippy.dependsOn(projectRefs: _*)
  Def.task(()).dependsOn(projectRefs.map(foo in _): _*)
}
