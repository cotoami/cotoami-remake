package marubinotto.libs.unified

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

// https://github.com/unifiedjs/unified
@js.native
@JSImport("unified",JSImport.Namespace)
object Unified extends js.Object {
  def unified(): Processor = js.native
}
