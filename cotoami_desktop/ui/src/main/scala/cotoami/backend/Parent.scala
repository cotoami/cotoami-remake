package cotoami.backend

import scala.scalajs.js

case class ParentNode(json: ParentNodeJson) {
  def nodeId: Id[Node] = Id(this.json.node_id)
}

@js.native
trait ParentNodeJson extends js.Object {
  val node_id: String = js.native
}
