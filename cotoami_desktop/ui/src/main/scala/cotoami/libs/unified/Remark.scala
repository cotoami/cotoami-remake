package cotoami.libs.unified

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

// https://github.com/remarkjs/remark/tree/main/packages/remark
@js.native
@JSImport("remark", JSImport.Namespace)
object Remark extends js.Object {
  def remark(): Processor = js.native
}
