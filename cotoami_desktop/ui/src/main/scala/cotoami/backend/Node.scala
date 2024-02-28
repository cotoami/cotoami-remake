package cotoami.backend

import scala.scalajs.js

@js.native
trait Node extends js.Object {
  val id: String = js.native
  val name: String = js.native
  val icon: Array[Byte] = js.native
}
