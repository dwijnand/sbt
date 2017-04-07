package sbt
package internal

import sbt.internal.util.complete.{ DefaultParsers, Parser }, DefaultParsers._
import sbt.librarymanagement.ModuleID

object AddSbtPluginCommand {
  // syntax choice
  //  1. addSbtPlugin("com.dwijnand" % "sbt-greeter" % "2.0.0-SNAPSHOT")
  //  2. addSbtPlugin "com.dwijnand" % "sbt-greeter" % "2.0.0-SNAPSHOT"
  //  3. applyPlugin "com.dwijnand" % "sbt-greeter" % "2.0.0-SNAPSHOT"
  //  4. ???

  def syntax = s"""addSbtPlugin("<groupId>" % "<artifactId>" % "<version>")"""
  def desc = "Adds the specified sbt plugin to the build"
  def detailed = s"$syntax\n\n  $desc"

  def cmd: Command = Command("addSbtPlugin", (syntax, desc), detailed)(_ => parser)(effect)

  // TODO: Currently tab completes "addSbtPlugin<TAB>" to "addSbtPlugin(    )"
  def parser: Parser[ModuleID] = {
    val groupId = token(StringBasic examples "\"groupId\"")
    val artifactId = token(StringBasic examples "\"artifactId\"")
    val version = token(StringBasic examples "\"version\"")
    val sep = token("%")
    val % = Space ~> sep ~> Space

    ("(" ~> groupId ~ (% ~> artifactId) ~ (% ~> version) <~ ")") map {
      case groupId ~ artifactId ~ version => ModuleID(groupId, artifactId, version)
    }
  }

  def effect(state: State, plugin: ModuleID): State = {
    state.log.info(s"Adding sbt plugin $plugin")
    import plugin.{ organization => groupId, name => artifactId, revision => version}
    "reload plugins" ::
      s"""set addSbtPlugin("$groupId" % "$artifactId" % "$version")""" ::
      "session save"  ::
      "reload return" ::
      state
  }
}
