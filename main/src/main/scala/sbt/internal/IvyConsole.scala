/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import sbt.io.IO

import sbt.internal.util.Attributed
import sbt.util.{ Level, Logger }

import sbt.librarymanagement.{
  CrossVersion,
  MavenRepository,
  ModuleID,
  Resolver
}
import sbt.librarymanagement.Configurations.Compile

import sbt.Def.Setting
import sbt.Keys._
import sbt.Scope.Global
import sbt.SlashSyntax0._

object IvyConsole {
  final val Name = "ivy-console"

  lazy val command = Command.command(Name) { state =>
    val args = state.remainingCommands.map(_.commandLine)
    val Dependencies(managed, repos, unmanaged) = parseDependencies(args, state.log)
    val base = new File(CommandUtil.bootDirectory(state), Name)
    IO.createDirectory(base)

    val (eval, structure) = Load.defaultLoad(state, base, state.log)
    val session = Load.initialSession(structure, eval)
    val extracted = Project.extract(session, structure)

    val depSettings: Seq[Setting[_]] = Seq(
      libraryDependencies ++= managed.reverse,
      resolvers ++= repos.reverse.toVector,
      Compile / unmanagedJars ++= Attributed blankSeq unmanaged.reverse,
      Global / logLevel        := Level.Warn,
      Global / showSuccess     := false,
    )

    extracted.appendWithoutSession(depSettings)
  }

  final case class Dependencies(
      managed: Seq[ModuleID],
      resolvers: Seq[Resolver],
      unmanaged: Seq[File],
  )

  def parseDependencies(args: Seq[String], log: Logger): Dependencies =
    args.foldLeft(Dependencies(Nil, Nil, Nil))(parseArgument(log))

  def parseArgument(log: Logger)(acc: Dependencies, arg: String): Dependencies =
    arg match {
      case _ if arg.contains(" at ") => acc.copy(resolvers = parseResolver(arg) +: acc.resolvers)
      case _ if arg.endsWith(".jar") => acc.copy(unmanaged = new File(arg) +: acc.unmanaged)
      case _                         => acc.copy(managed = parseManaged(arg, log) ++ acc.managed)
    }

  private[this] def parseResolver(arg: String): MavenRepository = {
    val Array(name, url) = arg.split(" at ")
    MavenRepository(name.trim, url.trim)
  }

  val DepPattern = """([^%]+)%(%?)([^%]+)%([^%]+)""".r

  def parseManaged(arg: String, log: Logger): Seq[ModuleID] =
    arg match {
      case DepPattern(group, cross, name, version) =>
        val crossV = if (cross.trim.isEmpty) CrossVersion.disabled else CrossVersion.binary
        ModuleID(group.trim, name.trim, version.trim).withCrossVersion(crossV) :: Nil
      case _ => log.warn(s"Ignoring invalid argument '$arg'"); Nil
    }
}
