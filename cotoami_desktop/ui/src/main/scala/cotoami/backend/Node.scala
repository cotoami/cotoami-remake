package cotoami.backend

import scala.scalajs.js
import cotoami.Validation

@js.native
trait Node extends js.Object {
  val uuid: String = js.native
  val name: String = js.native
  val icon: Array[Byte] = js.native
}

object Node {
  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)
}
