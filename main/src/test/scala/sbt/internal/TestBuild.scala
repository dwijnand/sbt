/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import scala.annotation.tailrec

import Def.{ ScopedKey, Setting }
import sbt.internal.util.{ AttributeKey, AttributeMap, Relation, Settings }
import sbt.internal.util.Types.{ const, some }
import sbt.internal.util.complete.Parser
import sbt.librarymanagement.Configuration

import java.net.URI
import org.scalacheck._
import Gen._

// Notes:
//  Generator doesn't produce cross-build project dependencies or do anything with the 'extra' axis
object TestBuild {
  val MaxTasks = 6
  val MaxProjects = 7
  val MaxConfigs = 5
  val MaxBuilds = 4
  val MaxIDSize = 8
  val MaxDeps = 8
  val KeysPerEnv = 10

  val MaxTasksGen = chooseShrinkable(1, MaxTasks)
  val MaxProjectsGen = chooseShrinkable(1, MaxProjects)
  val MaxConfigsGen = chooseShrinkable(1, MaxConfigs)
  val MaxBuildsGen = chooseShrinkable(1, MaxBuilds)
  val MaxDepsGen = chooseShrinkable(0, MaxDeps)

  def chooseShrinkable(min: Int, max: Int): Gen[Int] =
    sized(sz => choose(min, (max min sz) max 1))

  implicit val cGen = Arbitrary { genConfigs(scalaIDGen, MaxDepsGen, MaxConfigsGen) }
  implicit val tGen = Arbitrary { genTasks(lowerIDGen, MaxDepsGen, MaxTasksGen) }
  val seed = rng.Seed.random

  class TestKeys(val env: Env, val scopes: Seq[Scope]) {
    override def toString = env + "\n" + scopes.mkString("Scopes:\n\t", "\n\t", "")
    lazy val delegated = scopes map env.delegates
  }

  sealed case class Structure(
      env: Env,
      current: ProjectRef,
      data: Settings[Scope],
      keyIndex: KeyIndex,
      keyMap: Map[String, AttributeKey[_]]
  ) {
    override def toString = {
      s"""$env
         |current: $current
         |Settings:
         |  $showData
         |All keys:
	       |  ${keyMap.keys.mkString(", ")}""".stripMargin
    }

    def showKeys(map: AttributeMap): String = map.keys.mkString("\n\t   AttributeKey(", ",", ")")

    def showData: String = {
      val scopeStrings =
        for ((scope, map) <- data.data) yield (Scope.display(scope, "<key>"), showKeys(map))
      scopeStrings.toSeq.sorted.map(t => t._1 + t._2).mkString("\n\t")
    }

    val extra: BuildUtil[Proj] = {
      val getp = (build: URI, project: String) => env.buildMap(build).projectMap(project)
      new BuildUtil(
        keyIndex,
        data,
        env.root.uri,
        env.rootProject,
        getp,
        _.configurations.map(c => ConfigKey(c.name)),
        Relation.empty
      )
    }

    lazy val allAttributeKeys: Set[AttributeKey[_]] = {
      val x = data.data.values.flatMap(_.keys).toSet
      assert(x.nonEmpty, "allAttributeKeys is empty")
      x
    }
    lazy val (taskAxes, zeroTaskAxis, onlyTaskAxis, multiTaskAxis) = {
      import collection.mutable
      import mutable.HashSet

      // task axis of Scope is set to Zero and the value of the second map is the original task axis
      val taskAxesMappings =
        for ((scope, keys) <- data.data.toIterable; key <- keys.keys)
          yield
            (ScopedKey(scope.copy(task = Zero), key), scope.task): (
                ScopedKey[_],
                ScopeAxis[AttributeKey[_]]
            )

      val taskAxes = Relation.empty ++ taskAxesMappings
      val zero = new HashSet[ScopedKey[_]]
      val single = new HashSet[ScopedKey[_]]
      val multi = new HashSet[ScopedKey[_]]
      for ((skey, tasks) <- taskAxes.forwardMap) {
        def makeKey(task: ScopeAxis[AttributeKey[_]]) =
          ScopedKey(skey.scope.copy(task = task), skey.key)
        val hasGlobal = tasks(Zero)
        if (hasGlobal)
          zero += skey
        else {
          val keys = tasks map makeKey
          keys.size match {
            case 0 =>
            case 1 => single ++= keys
            case _ => multi ++= keys
          }
        }
      }
      (taskAxes, zero.toSet, single.toSet, multi.toSet)
    }
  }

  final case class Env(builds: Vector[Build], tasks: Vector[Taskk]) {
    override def toString = {
      def seqToString[A](xs: Seq[A]) = xs match {
        case Seq()  => ""
        case Seq(x) => s" $x"
        case _ =>
          s"""
             |    ${xs.mkString("\n    ")}""".stripMargin
      }
      s"""Env:
         |    Tasks:${seqToString(tasks)}
         |    Builds:${seqToString(builds)}
         |""".stripMargin.trim
    }

    val root = builds.head
    val buildMap = mapBy(builds)(_.uri)
    val taskMap = mapBy(tasks)(getKey)
    def project(ref: ProjectRef) = buildMap(ref.build).projectMap(ref.project)
    def projectFor(ref: ResolvedReference) = ref match {
      case pr: ProjectRef => project(pr); case BuildRef(uri) => buildMap(uri).root
    }

    lazy val allProjects = builds.flatMap(_.allProjects)
    def rootProject(uri: URI): String = buildMap(uri).root.id
    def inheritConfig(ref: ResolvedReference, config: ConfigKey) =
      projectFor(ref).confMap(config.name).extendsConfigs map toConfigKey
    def inheritTask(task: AttributeKey[_]) = taskMap.get(task) match {
      case None    => Vector()
      case Some(t) => t.delegates.toVector map getKey
    }
    def inheritProject(ref: ProjectRef) = project(ref).delegates.toVector
    def resolve(ref: Reference) = Scope.resolveReference(root.uri, rootProject, ref)
    lazy val delegates: Scope => Seq[Scope] =
      Scope.delegates(
        allProjects,
        (_: Proj).configurations.toVector.map(toConfigKey),
        resolve,
        uri => buildMap(uri).root.id,
        inheritProject,
        inheritConfig,
        inheritTask,
      )
    lazy val allFullScopes: Seq[Scope] =
      for {
        (ref, p) <- (Zero, root.root) +: allProjects.map { case (ref, p) => (Select(ref), p) }
        t <- Zero +: tasks.map(t => Select(t.key))
        c <- Zero +: p.configurations.map(c => Select(ConfigKey(c.name)))
      } yield Scope(project = ref, config = c, task = t, extra = Zero)
  }

  def getKey: Taskk => AttributeKey[_] = _.key
  def toConfigKey: Configuration => ConfigKey = c => ConfigKey(c.name)

  case class Build(uri: URI, projects: Seq[Proj]) {
    override def toString = "BuildRef(" + uri + "):\n    " + projects.mkString("\n      ")

    val allProjects = projects map (p => (ProjectRef(uri, p.id), p))
    val root = projects.head
    val projectMap = mapBy(projects)(_.id)
  }

  case class Proj(
      id: String,
      delegates: Seq[ProjectRef],
      configurations: Seq[Configuration]
  ) {
    override def toString = {
      def seqToString[A](xs: Seq[A], name: String) = xs match {
        case Seq()  => ""
        case Seq(x) => "\n      " + name + ": " + x
        case _ =>
          smashFold("\n      " + name + ":\n        ", xs.mkString("\n        "), "")
      }
      s"Project $id" +
        seqToString(delegates, "Delegates") +
        seqToString(configurations, "Configurations")
    }

    val confMap = mapBy(configurations)(_.name)
  }

  // Pseudo TaskKey
  case class Taskk(key: AttributeKey[String], delegates: Seq[Taskk]) {
    override def toString =
      key.label + smashFold(" (delegates: ", delegates.map(_.key.label).mkString(", "), ")")
  }

  def smashFold[A](prefix: String, xs: String, suffix: String) =
    if (xs.isEmpty) "" else prefix + xs + suffix

  def mapBy[K, T](s: Seq[T])(f: T => K): Map[K, T] = s.map(t => (f(t), t)).toMap

  implicit lazy val arbKeys: Arbitrary[TestKeys] = Arbitrary(keysGen)
  lazy val keysGen: Gen[TestKeys] = for (env <- mkEnv; keyCount <- chooseShrinkable(1, KeysPerEnv);
                                         keys <- listOfN(keyCount, scope(env)))
    yield new TestKeys(env, keys)

  def scope(env: Env): Gen[Scope] =
    for {
      build <- oneOf(env.builds)
      project <- oneOf(build.projects)
      cAxis <- oneOrGlobal(project.configurations map toConfigKey)
      tAxis <- oneOrGlobal(env.tasks map getKey)
      pAxis <- orGlobal(frequency((1, BuildRef(build.uri)), (3, ProjectRef(build.uri, project.id))))
    } yield Scope(pAxis, cAxis, tAxis, Zero)

  def orGlobal[T](gen: Gen[T]): Gen[ScopeAxis[T]] =
    frequency((1, gen map Select.apply), (1, Zero))
  def oneOrGlobal[T](gen: Seq[T]): Gen[ScopeAxis[T]] = orGlobal(oneOf(gen))

  def makeParser(structure: Structure): Parser[ScopedKey[_]] = {
    import structure._
    def confs(uri: URI) =
      env.buildMap.get(uri).toList.flatMap { _.root.configurations.map(_.name) }
    val defaultConfs: Option[ResolvedReference] => Seq[String] = {
      case None                  => confs(env.root.uri)
      case Some(BuildRef(uri))   => confs(uri)
      case Some(ref: ProjectRef) => env.project(ref).configurations.map(_.name)
    }
    Act.scopedKey(keyIndex, current, defaultConfs, keyMap, data)
  }

  def structure(env: Env, settings: Seq[Setting[_]], current: ProjectRef): Structure = {
    if (settings.isEmpty)
      try sys.error("settings is empty")
      catch { case e: Throwable => e.printStackTrace(); throw e }
    implicit val display = Def.showRelativeKey2(current)
    val data = Def.make(settings)(env.delegates, const(Nil), display)
    val keys = data.allKeys((s, key) => ScopedKey(s, key))
    val keyMap = keys.map(k => (k.key.label, k.key)).toMap[String, AttributeKey[_]]
    val projectsMap = env.builds.map(b => (b.uri, b.projects.map(_.id).toSet)).toMap
    val projs = for (b <- env.builds; p <- b.projects) yield p
    val confs = projs.map(p => p.id -> p.configurations).toMap
    Structure(env, current, data, KeyIndex(keys, projectsMap, confs), keyMap)
  }

  implicit lazy val mkEnv: Gen[Env] = {
    val pGen = (uri: URI) => genProjects(uri)(idGen, MaxDepsGen, MaxProjectsGen, cGen.arbitrary)
    envGen(buildGen(uriGen, pGen), tGen.arbitrary)
  }

  implicit def maskGen(implicit arbBoolean: Arbitrary[Boolean]): Gen[ScopeMask] = {
    val b = arbBoolean.arbitrary
    for (p <- b; c <- b; t <- b; x <- b)
      yield ScopeMask(project = p, config = c, task = t, extra = x)
  }

  // exclude 0xFFFE due to this bug: http://bit.ly/1QryQZy
  // also exclude 0xFFFF as it is not unicode: http://bit.ly/2cVBrzK
  val allChars: Seq[Char] = ((0x0000 to 0xD7FF) ++ (0xE000 to 0xFFFD)).map(_.toChar)

  val letters: Seq[Char] = allChars.filter(_.isLetter)

  val upperLetters: Gen[Char] = oneOf(letters.filter(_.isUpper))
  val lowerLetters: Gen[Char] = oneOf(letters.filter(_.isLower))
  val lettersAndDigits: Gen[Char] = oneOf(allChars.filter(_.isLetterOrDigit))

  val scalaIDCharGen: Gen[Char] = frequency(19 -> lettersAndDigits, 1 -> Gen.const('_'))
  val idCharGen: Gen[Char] = frequency(19 -> scalaIDCharGen, 1 -> Gen.const('-'))

  def isIDChar(c: Char) = c.isLetterOrDigit || "-_".toSeq.contains(c)

  val idGen: Gen[String] = idGen(upperLetters, idCharGen, _.isUpper)
  val lowerIDGen: Gen[String] = idGen(lowerLetters, idCharGen, _.isLower)
  val scalaIDGen: Gen[String] = idGen(upperLetters, scalaIDCharGen, _.isUpper)

  def idGen(start: Gen[Char], end: Gen[Char], headFilter: Char => Boolean): Gen[String] = {
    for {
      size <- chooseShrinkable(1, MaxIDSize)
      idStart <- start
      idEnd <- listOfN(size - 1, end)
    } yield idStart + idEnd.mkString
  }

  val schemeGen: Gen[String] = {
    for {
      schemeStart <- alphaChar
      schemeEnd <- listOf(frequency(19 -> alphaNumChar, 1 -> oneOf('+', '-', '.')))
    } yield schemeStart + schemeEnd.mkString
  }

  val uriChar: Gen[Char] = {
    frequency(9 -> alphaNumChar, 1 -> oneOf(";/?:@&=+$,-_.!~*'()".toSeq))
  }

  val uriStringGen: Gen[String] = nonEmptyListOf(uriChar).map(_.mkString)

  val optIDGen: Gen[Option[String]] =
    frequency(9 -> uriStringGen.map(some.fn), 1 -> Gen.const(None))

  val uriGen: Gen[URI] = {
    for {
      sch <- schemeGen
      ssp <- uriStringGen
      frag <- optIDGen
    } yield new URI(sch, ssp, frag.orNull)
  }

  implicit def envGen(implicit bGen: Gen[Build], tasks: Gen[Vector[Taskk]]): Gen[Env] =
    for (i <- MaxBuildsGen; bs <- containerOfN[Vector, Build](i, bGen); ts <- tasks)
      yield Env(bs, ts)

  implicit def buildGen(implicit uGen: Gen[URI], pGen: URI => Gen[Seq[Proj]]): Gen[Build] =
    for (u <- uGen; ps <- pGen(u)) yield Build(u, ps)

  def nGen[T](igen: Gen[Int])(implicit g: Gen[T]): Gen[Vector[T]] =
    igen flatMap (ig => containerOfN[Vector, T](ig, g))

  implicit def genProjects(build: URI)(
      implicit genID: Gen[String],
      maxDeps: Gen[Int],
      count: Gen[Int],
      confs: Gen[Seq[Configuration]]
  ): Gen[Seq[Proj]] =
    genAcyclic(maxDeps, genID, count) { (id: String) =>
      for (cs <- confs) yield { (deps: Seq[Proj]) =>
        Proj(id, deps.map(dep => ProjectRef(build, dep.id)), cs)
      }
    }

  def genConfigs(
      implicit genName: Gen[String],
      maxDeps: Gen[Int],
      count: Gen[Int]
  ): Gen[Vector[Configuration]] =
    genAcyclicDirect[Configuration, String](maxDeps, genName, count)(
      (key, deps) => Configuration.of(key.capitalize, key).withExtendsConfigs(deps)
    )

  def genTasks(
      implicit genName: Gen[String],
      maxDeps: Gen[Int],
      count: Gen[Int]
  ): Gen[Vector[Taskk]] =
    genAcyclicDirect[Taskk, String](maxDeps, genName, count)(
      (key, deps) => Taskk(AttributeKey[String](key), deps)
    )

  def genAcyclicDirect[A, T](maxDeps: Gen[Int], keyGen: Gen[T], max: Gen[Int])(
      make: (T, Vector[A]) => A
  ): Gen[Vector[A]] =
    genAcyclic[A, T](maxDeps, keyGen, max) { t =>
      Gen.const { deps =>
        make(t, deps)
      }
    }

  def genAcyclic[A, T](maxDeps: Gen[Int], keyGen: Gen[T], max: Gen[Int])(
      make: T => Gen[Vector[A] => A]
  ): Gen[Vector[A]] =
    max flatMap { count =>
      containerOfN[Vector, T](count, keyGen)
        .flatMap(keys => genAcyclic(maxDeps, keys.distinct)(make))
    }

  def genAcyclic[A, T](maxDeps: Gen[Int], keys: Vector[T])(
      make: T => Gen[Vector[A] => A]
  ): Gen[Vector[A]] =
    genAcyclic(maxDeps, keys, Vector()) flatMap { pairs =>
      sequence(pairs.map { case (key, deps) => mapMake(key, deps, make) }) flatMap { inputs =>
        val made = new collection.mutable.HashMap[T, A]
        for ((key, deps, mk) <- inputs)
          made(key) = mk(deps map made)
        keys map made
      }
    }

  def mapMake[A, T](key: T, deps: Vector[T], make: T => Gen[Vector[A] => A]): Gen[Inputs[A, T]] =
    make(key) map ((mk: Vector[A] => A) => (key, deps, mk))

  @tailrec def genAcyclic[T](
      maxDeps: Gen[Int],
      names: Vector[T],
      acc: Vector[Gen[(T, Vector[T])]]
  ): Gen[Vector[(T, Vector[T])]] =
    names match {
      case Vector() => sequence(acc)
      case Vector(x, xs @ _*) =>
        val next = for (depCount <- maxDeps; d <- pick(depCount min xs.size, xs))
          yield (x, d.toVector)
        genAcyclic(maxDeps, xs.toVector, next +: acc)
    }

  def sequence[T](gs: Vector[Gen[T]]): Gen[Vector[T]] = Gen.parameterized { prms =>
    delay(gs map (g => g(prms, seed) getOrElse sys.error("failed generator")))
  }

  type Inputs[A, T] = (T, Vector[T], Vector[A] => A)
}
