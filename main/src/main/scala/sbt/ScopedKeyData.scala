/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.Types.const
import sbt.Def.ScopedKey

final case class ScopedKeyData[A](scoped: ScopedKey[A], value: Any) {
  val key = scoped.key
  val scope = scoped.scope

  def typeName: String = fold(fmtMf("Task[%s]"), fmtMf("InputTask[%s]"), manifest.toString)

  def settingValue: Option[Any] = fold(const(None), const(None), Some(value))

  def description: String =
    fold(fmtMf("Task: %s"), fmtMf("Input task: %s"), s"Setting: $manifest = $value")

  def fold[T](targ: OptManifest[_] => T, itarg: OptManifest[_] => T, s: => T): T =
    manifest.runtimeClass match {
      case TaskClass      => targ(manifest.typeArguments.head)
      case InputTaskClass => itarg(manifest.typeArguments.head)
      case _              => s
    }

  def fmtMf(s: String): OptManifest[_] => String = s.format(_)

  private def manifest = key.manifest
  private val TaskClass = classOf[Task[_]]
  private val InputTaskClass = classOf[InputTask[_]]
}
