package cotoami.backend

import scala.scalajs.js

case class DatabaseInfo(json: DatabaseInfoJson) {
  def folder: String = this.json.folder
  def lastChangeNumber: Double = this.json.last_change_number

  lazy val nodes: Map[Id[Node], Node] =
    this.json.nodes.map(Node(_)).map(node => (node.id, node)).toMap

  def localNodeId: Id[Node] = Id(this.json.local_node_id)

  def localNode: Option[Node] =
    this.nodes.get(this.localNodeId)

  lazy val parentNodeIds: Seq[Id[Node]] =
    this.json.parent_node_ids.map(Id[Node](_)).toSeq

  def debug: String = {
    val s = new StringBuilder
    s ++= s"folder: ${this.folder}"
    s ++= s", lastChangeNumber: ${this.lastChangeNumber}"
    s ++= s", nodes: ${this.nodes.size}"
    s ++= s", localNode: {${this.localNode.map(_.debug)}}"
    s ++= s", parentNodes: ${this.parentNodeIds.size}"
    s.result()
  }
}

@js.native
trait DatabaseInfoJson extends js.Object {
  val folder: String = js.native
  val last_change_number: Double = js.native
  val nodes: js.Array[NodeJson] = js.native
  val local_node_id: String = js.native
  val parent_node_ids: js.Array[String] = js.native
}
