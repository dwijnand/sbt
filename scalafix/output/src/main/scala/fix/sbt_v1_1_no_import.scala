package fix

import sbt.{ Global, ThisBuild }
import sbt.Keys.cancelable
import sbt.Zero

object sbt_v1_1_no_import_Test {
  ThisBuild / Zero / Zero / cancelable
}
