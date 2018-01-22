package sbt
package internal

import org.specs2._, execute.Typecheck._

object TaskSequentialSpec extends Specification {
  def is = s2"""

  Def.sequential
    can be called within other sbt macros             $nestingMacros
                                                      """

  def nestingMacros = {
    import Keys._
    val foo: Boolean = false
    Def.taskDyn {
      if (foo) {
        Def.sequential(clean, clean)
      } else Def.task(())
    }
    ok
  }

}
