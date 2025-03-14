package cotoami.libs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

// https://github.com/wooorm/lowlight
@js.native
@JSImport("lowlight", JSImport.Namespace)
object lowlight extends js.Object {

  /** Map of all (±190) grammars
    */
  val all: js.Object = js.native

  /** Map of common (37) grammars
    */
  val common: js.Object = js.native
}
