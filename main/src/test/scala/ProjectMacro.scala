package sbt

import org.ensime.pcplod._
import org.scalacheck._
import Prop._
import Project.project
import java.io.File

class ProjectDefs {
  lazy val p = project

  val x = project

  // should not compile
  // def y = project

  val z = project in new File("dir")

  val a: Project = project

  lazy val aa: Project = project
}

object ProjectMacro extends Properties("ProjectMacro") {
  lazy val pd = new ProjectDefs
  import pd._

  def secure(f: => Prop): Prop = try {
    Prop.secure(f)
  } catch {
    case e: Throwable =>
      e.printStackTrace
      throw e
  }

  property("Explicit type on lazy val supported") = secure(check(aa, "aa", "aa"))
  property("Explicit type on val supported") = secure(check(a, "a", "a"))
  property("lazy vals supported") = secure(check(p, "p", "p"))
  property("plain vals supported") = secure(check(x, "x", "x"))
  property("Directory overridable") = secure(check(z, "z", "dir"))

  def check(p: Project, id: String, dir: String): Prop =
    {
      s"Expected id: $id" |:
        s"Expected dir: $dir" |:
        s"Actual id: ${p.id}" |:
        s"Actual dir: ${p.base}" |:
        (p.id == id) &&
        (p.base.getName == dir)
    }

  property("no msgs") = pcCheck(_.messages ?= Nil)

  property("aaa") = pcCheck(_.infoAt('aaa) ?= (Some(s"Build.aaa") -> Some("sbt.Project")))
  property("p_a") = pcCheck(_.infoAt('p_a) ?= (Some("sbt.Project.project") -> Some("sbt.Project")))

  property("bbb") = pcCheck(_.infoAt('bbb) ?= (Some(s"Build.bbb") -> Some("sbt.Project")))
  property("p_b") = pcCheck(_.infoAt('p_b) ?= (Some("sbt.Project.project") -> Some("sbt.Project")))

  property("ccc") = pcCheck(_.infoAt('ccc) ?= (Some(s"Build.ccc") -> Some("sbt.Project")))
  property("p_c") = pcCheck(_.infoAt('p_c) ?= (Some("sbt.Project.project") -> Some("sbt.Project")))

  property("ddd") = pcCheck(_.infoAt('ddd) ?= (Some(s"Build.ddd") -> Some("sbt.Project")))
  property("p_d") = pcCheck(_.infoAt('p_d) ?= (Some("sbt.Project.project") -> Some("sbt.Project")))

  // TODO: Add a setting and verify it's not also sbt.Project

  def pcCheck(f: MrPlod => Prop): Prop = secure(withMrPlod("macros/Build.scala")(f))

  implicit class MrPlodOps(val _mr: MrPlod) extends AnyVal {
    def infoAt(p: Point) = (_mr.symbolAtPoint(p), _mr.typeAtPoint(p))
  }
}
