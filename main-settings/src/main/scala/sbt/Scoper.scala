/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.AttributeKey
import sbt.librarymanagement.Configuration

trait Scoper[A] {
  def scope(scope: Scope, x: A): Scope
}

object Scoper {
  implicit val scopeP: Scoper[Reference] = (sc, p) => sc.copy(project = Select(p))

  implicit val scopeBR: Scoper[BuildReference] = (sc, p) => sc.copy(project = Select(p))
  implicit val scopePR: Scoper[ProjectReference] = (sc, p) => sc.copy(project = Select(p))
  implicit val scopeRR: Scoper[ResolvedReference] = (sc, p) => sc.copy(project = Select(p))

  implicit val scopeBRef: Scoper[BuildRef] = (sc, p) => sc.copy(project = Select(p))
  implicit val scopeTB: Scoper[ThisBuild.type] = (sc, p) => sc.copy(project = Select(p))

  implicit val scopeLRP: Scoper[LocalRootProject.type] = (sc, p) => sc.copy(project = Select(p))
  implicit val scopePRef: Scoper[ProjectRef] = (sc, p) => sc.copy(project = Select(p))
  implicit val scopeTP: Scoper[ThisProject.type] = (sc, p) => sc.copy(project = Select(p))
  implicit val scopeLP: Scoper[LocalProject] = (sc, p) => sc.copy(project = Select(p))
  implicit val scopeRP: Scoper[RootProject] = (sc, p) => sc.copy(project = Select(p))

  implicit val scopeCK: Scoper[ConfigKey] = (sc, c) => sc.copy(config = Select(c))
  implicit val scopeC: Scoper[Configuration] = (sc, c) => sc.copy(config = Select(c))

  implicit def scopeT[A]: Scoper[AttributeKey[A]] = (sc, k) => sc.copy(task = Select(k))
}
