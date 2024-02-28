package cotoami

import scala.scalajs.js

package object backend {

  @js.native
  trait Error extends js.Object {
    val code: String = js.native
    val message: String = js.native
  }
}
