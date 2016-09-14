package sbt

import scala.util.control.NonFatal

import org.ensime.pcplod._
import org.scalacheck._
import Prop._

object MacrosTest extends Properties("MacrosTest") {
  def secure(f: => Prop): Prop = try Prop.secure(f) catch {
    case NonFatal(e) => sys.error(e.getClass + ": " + e.getMessage); throw e
  }

  def pcCheck(f: MrPlod => Prop): Prop = secure(withMrPlod("macros/Build.scala")(f))

  property("no pc messages") = pcCheck(_.messages ?= Nil)
}
