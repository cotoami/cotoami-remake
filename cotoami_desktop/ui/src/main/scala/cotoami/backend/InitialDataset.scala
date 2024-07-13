package cotoami.backend

import scala.scalajs.js
import fui.Cmd
import cotoami.tauri

case class InitialDataset(json: InitialDatasetJson) {
  def lastChangeNumber: Double = this.json.last_change_number

  lazy val nodes: Map[Id[Node], Node] =
    this.json.nodes.map(Node(_)).map(node => (node.id, node)).toMap

  def localNodeId: Id[Node] = Id(this.json.local_node_id)

  def localNode: Option[Node] =
    this.nodes.get(this.localNodeId)

  lazy val parentNodeIds: js.Array[Id[Node]] =
    this.json.parent_node_ids.map(Id[Node](_))

  lazy val servers: js.Array[Server] = this.json.servers.map(Server(_))

  def debug: String = {
    val s = new StringBuilder
    s ++= s"lastChangeNumber: ${this.lastChangeNumber}"
    s ++= s", nodes: ${this.nodes.size}"
    s ++= s", localNode: {${this.localNode.map(_.debug)}}"
    s ++= s", parentNodes: ${this.parentNodeIds.size}"
    s ++= s", servers: ${this.servers.size}"
    s.result()
  }
}

object InitialDataset {
  def switchOperatingNodeTo(
      parentId: Option[Id[Node]]
  ): Cmd[Either[ErrorJson, InitialDataset]] =
    InitialDatasetJson.switchOperatingNodeTo(parentId)
      .map(_.map(InitialDataset(_)))
}

@js.native
trait InitialDatasetJson extends js.Object {
  val last_change_number: Double = js.native
  val nodes: js.Array[NodeJson] = js.native
  val local_node_id: String = js.native
  val parent_node_ids: js.Array[String] = js.native
  val servers: js.Array[ServerJson] = js.native
}

object InitialDatasetJson {
  def switchOperatingNodeTo(
      parentId: Option[Id[Node]]
  ): Cmd[Either[ErrorJson, InitialDatasetJson]] =
    tauri
      .invokeCommand(
        "operate_as",
        js.Dynamic
          .literal(
            parentId = parentId.map(_.uuid).getOrElse(null)
          )
      )
}
