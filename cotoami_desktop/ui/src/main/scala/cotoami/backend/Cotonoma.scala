package cotoami.backend

import scala.scalajs.js
import cotoami.Validation

@js.native
trait Cotonoma extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val coto_id: String = js.native
  val name: String = js.native
  val posts: Int = js.native
}

object Cotonoma {
  val NameMaxLength = 50

  def validateName(name: String): Seq[Validation.Error] = {
    Vector(
      Validation.nonBlank(name),
      Validation.length(name, 1, NameMaxLength)
    ).flatten
  }
}
