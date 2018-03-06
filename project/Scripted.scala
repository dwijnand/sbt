import java.lang.reflect.InvocationTargetException

import sbt._
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.{ ClasspathUtilities, FilteredLoader }

object ScriptedPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  object autoImport extends ScriptedKeys {
    def scriptedPath = file("scripted")
  }

  import autoImport._

  override def globalSettings = super.globalSettings ++ Seq(
    scriptedBufferLog := true,
    scriptedPrescripted := { _ => },
  )
}

trait ScriptedKeys {
  val publishAll = taskKey[Unit]("")
  val publishLocalBinAll = taskKey[Unit]("")
  val scripted = inputKey[Unit]("")
  val scriptedUnpublished = inputKey[Unit]("Execute scripted without publishing sbt first. " +
        "Saves you some time when only your test has changed")
  val scriptedSource = settingKey[File]("")
  val scriptedPrescripted = taskKey[File => Unit]("")
  val scriptedBufferLog = settingKey[Boolean]("")
  val scriptedLaunchOpts = settingKey[Seq[String]]("options to pass to jvm launching scripted tasks")
}

object Scripted {
  // This is to workaround https://github.com/sbt/io/issues/110
  sys.props.put("jna.nosys", "true")

  val RepoOverrideTest = config("repoOverrideTest") extend Compile

  import sbt.complete._

  // Paging, 1-index based.
  final case class ScriptedTestPage(page: Int, total: Int)

  // FIXME: Duplicated with ScriptedPlugin.scriptedParser, this can be
  // avoided once we upgrade build.properties to 0.13.14
  def scriptedParser(scriptedBase: File): Parser[Seq[String]] = {
    import DefaultParsers._

    // these can be removed when we upgrade to sbt 1.2.0
    val NonZeroDigitSet = Set('1', '2', '3', '4', '5', '6', '7', '8', '9')
    val NonZeroDigit = (
      charClass(NonZeroDigitSet contains _, "non-zero digit")
        examples (NonZeroDigitSet map (_.toString))
    )
    val NonZeroNatBasic = mapOrFail(NonZeroDigit ~ Digit.*)(x => (x._1 +: x._2).mkString.toInt)

    val scriptedFiles = ("test": NameFilter) | "pending"

    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map {
      (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
    }
    val pairMap = pairs.groupBy(_._1) map { case (group, files) => group -> files.map(_._2).toSet }

    val id = charClass(c => !c.isWhitespace && c != '/').+.string
    val groupP = token(id.examples(pairMap.keySet)) <~ token('/')

    // A parser for page definitions
    val pageP: Parser[ScriptedTestPage] = ("*" ~ NonZeroNatBasic ~ "of" ~ NonZeroNatBasic) map {
      case _ ~ page ~ _ ~ total => ScriptedTestPage(page, total)
    }

    // Grabs the filenames from a given test group in the current page definition.
    def pagedFilenames(group: String, page: ScriptedTestPage): Seq[String] = {
      val files = pairMap(group).toSeq.sortBy(_.toLowerCase)
      val pageSize = files.size / page.total
      // The last page may loose some values, so we explicitly keep them
      val dropped = files.drop(pageSize * (page.page - 1))
      if (page.page == page.total) dropped
      else dropped.take(pageSize)
    }

    def nameP(group: String) =
      token("*".id | id.examples(pairMap.getOrElse(group, Set.empty[String])))

    val PagedIds: Parser[Seq[String]] =
      for {
        group <- groupP
        page <- pageP
        files = pagedFilenames(group, page)
        // TODO -  Fail the parser if we don't have enough files for the given page size
        //if !files.isEmpty
      } yield files map (f => s"$group/$f")

    val testID = (for (group <- groupP; name <- nameP(group)) yield (group, name))
    val testIdAsGroup = matched(testID) map (test => Seq(test))

    //(token(Space) ~> matched(testID)).*
    (token(Space) ~> (PagedIds | testIdAsGroup)).* map (_.flatten)
  }

  def doScripted(
      launcher: File,
      scriptedSbtClasspath: Seq[Attributed[File]],
      scriptedSbtInstance: ScalaInstance,
      sourcePath: File,
      bufferLog: Boolean,
      args: Seq[String],
      prescripted: File => Unit,
      launchOpts: Seq[String],
  ): Unit = {
    System.err.println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")

    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"

    val noJLine = new FilteredLoader(scriptedSbtInstance.loader, "jline." :: Nil)
    val loader = ClasspathUtilities.toLoader(scriptedSbtClasspath.files, noJLine)
    val bridgeClass = Class.forName("sbt.scriptedtest.ScriptedRunner", true, loader)

    // Interface to cross class loader
    type SbtScriptedRunner = {
      def runInParallel(
        resourceBaseDirectory: File,
        bufferLog: Boolean,
        tests: Array[String],
        bootProperties: File,
        launchOpts: Array[String],
        prescripted: java.util.List[File],
      ): Unit
    }

    val bridge = bridgeClass.getDeclaredConstructor().newInstance().asInstanceOf[SbtScriptedRunner]

    try {
      // Using java.util.List to encode File => Unit.
      val callback = new java.util.AbstractList[File] {
        override def add(x: File): Boolean = { prescripted(x); false }
        def get(x: Int): sbt.File = ???
        def size(): Int = 0
      }
      import scala.language.reflectiveCalls
      bridge.runInParallel(
        sourcePath,
        bufferLog,
        args.toArray,
        launcher,
        launchOpts.toArray,
        callback,
      )
    } catch { case ite: InvocationTargetException => throw ite.getCause }
  }
}
