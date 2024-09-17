package cotoami.backend

import scala.scalajs.js
import java.time.Instant

import cotoami.models.Node

case class ChildNode(json: ChildNodeJson) {
  def nodeId: Id[Node] = Id(this.json.node_id)
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  def asOwner: Boolean = this.json.as_owner
  def canEditLinks: Boolean = this.json.can_edit_links
}

@js.native
trait ChildNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val as_owner: Boolean = js.native
  val can_edit_links: Boolean = js.native
}
