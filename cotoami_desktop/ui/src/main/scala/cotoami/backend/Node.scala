package cotoami.backend

import scala.scalajs.js
import cotoami.{Id, Validation}

case class Node(json: NodeJson) {
  def id(): Id[Node] = Id(this.json.uuid)
  def name(): String = this.json.name
  def icon(): String = this.json.icon
  def rootCotonomaId(): Id[Cotonoma] = Id(this.json.root_cotonoma_id)
  def version(): Int = this.json.version

  def debug(): String =
    s"id: ${this.id()}, name: ${this.name()}, version: ${this.version()}"
}

object Node {
  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)
}

@js.native
trait NodeJson extends js.Object {
  val uuid: String = js.native
  val name: String = js.native
  val icon: String = js.native // Base64 encoded image binary
  val root_cotonoma_id: String = js.native
  val version: Int = js.native
}
