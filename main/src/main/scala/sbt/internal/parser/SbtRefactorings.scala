/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import sbt.internal.parser.SbtParser.{ END_OF_LINE, FAKE_FILE }
import sbt.internal.SessionSettings.{ SessionSetting, SbtConfigFile }

private[sbt] object SbtRefactorings {
  val emptyString = ""
  val reverseOrderingInt = Ordering[Int].reverse

  /**
   * Refactoring a `.sbt` file so that the new settings are used instead of any existing settings.
   * @param configFile SbtConfigFile with the lines of an sbt file as a List[String] where each string is one line
   * @param commands A List of settings (space separate) that should be inserted into the current file.
   *                 If the settings replaces a value, it will replace the original line in the .sbt file.
   *                 If in the `.sbt` file we have multiply value for one settings -
   *                 the first will be replaced and the other will be removed.
   * @return a SbtConfigFile with new lines which represent the contents of the refactored .sbt file.
   */
  def applySessionSettings(
      configFile: SbtConfigFile,
      commands: Seq[SessionSetting]
  ): SbtConfigFile = {
    val (file, lines) = configFile
    val recordedCommands =
      recordCommands(commands, SbtParser(FAKE_FILE, lines)).sortBy(_._1)(reverseOrderingInt)
    val newContent = replaceFromBottomToTop(lines.mkString(END_OF_LINE), recordedCommands)
    (file, newContent.linesIterator.toList)
  }

  private def recordCommands(commands: Seq[SessionSetting], parser: SbtParser) =
    for {
      (_, command) <- commands
      (name, _) <- toTreeStringMap(command)
      replacements <- treesToReplacements(parser, name, command)
    } yield replacements

  private def toTreeStringMap(command: Seq[String]) =
    SbtParser(FAKE_FILE, command).settingsTrees.map {
      case (statement, tree) => (extractSettingName(tree), statement)
    }.toMap

  private def replaceFromBottomToTop(
      modifiedContent: String,
      sortedRecordedCommands: Seq[(Int, String, String)]
  ) = {
    sortedRecordedCommands.foldLeft(modifiedContent) {
      case (acc, (from, old, replacement)) =>
        val before = acc.substring(0, from)
        val after = blankToEmpty(acc.substring(from + old.length, acc.length))
        before + replacement + after
    }
  }

  private def treesToReplacements(parser: SbtParser, name: String, command: Seq[String]) =
    parser.settingsTrees.iterator
      .filter { case (_, tree) => name == extractSettingName(tree) }
      .foldLeft(Seq.empty[(Int, String, String)]) {
        case (acc, (st, tree)) =>
          val replacement = if (acc.nonEmpty) emptyString else command.mkString(END_OF_LINE)
          (tree.pos.start, st, replacement) +: acc
      }

  private def extractSettingName(tree: scala.tools.nsc.Global#Tree): String =
    tree.children.headOption.fold(tree.toString())(extractSettingName)

  private def blankToEmpty(text: String) = {
    val trimmed = text.trim
    if (trimmed.isEmpty) trimmed else text
  }
}
