package cotoami.backend

import scala.scalajs.js

case class ChildNode(json: ChildNodeJson) {
  def nodeId: Id[Node] = Id(this.json.node_id)
}

@js.native
trait ChildNodeJson extends js.Object {
  val node_id: String = js.native
}
