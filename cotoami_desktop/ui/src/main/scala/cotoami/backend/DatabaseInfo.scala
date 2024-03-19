package cotoami.backend

import scala.scalajs.js

@js.native
trait DatabaseInfo extends js.Object {
  val folder: String = js.native
  val nodes: js.Array[Node] = js.native
  val local_node_id: String = js.native
  val parent_node_ids: js.Array[String] = js.native
}

object DatabaseInfo {
  def nodes_as_map(info: DatabaseInfo): Map[String, Node] =
    info.nodes.map(node => (node.uuid, node)).toMap

  def debug(info: DatabaseInfo): String = {
    val localNode = info.nodes.find(_.uuid == info.local_node_id)
    val s = new StringBuilder
    s ++= s"folder: ${info.folder}"
    s ++= s", nodes: ${info.nodes.size}"
    s ++= s", localNode: {${localNode.map(Node.debug(_))}}"
    s ++= s", parentNodes: ${info.parent_node_ids.size}"
    s.result()
  }
}
