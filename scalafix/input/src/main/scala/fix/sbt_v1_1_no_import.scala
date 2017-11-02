/*
rule = "class:fix.sbt_v1_1"
*/
package fix

import sbt.{ Global, ThisBuild }
import sbt.Keys.cancelable

object sbt_v1_1_no_import_Test {
  cancelable in Global in ThisBuild
}
