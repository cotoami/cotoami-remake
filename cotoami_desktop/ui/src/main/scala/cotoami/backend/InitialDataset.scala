package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.libs.tauri
import cotoami.models.{Coto, Cotonoma, Id, Node, Server}

case class InitialDataset(json: InitialDatasetJson) {
  def lastChangeNumber: Double = json.last_change_number

  lazy val nodes: Map[Id[Node], Node] =
    json.nodes
      .map(NodeBackend.toModel(_))
      .map(node => node.id -> node)
      .toMap

  lazy val nodeRoots: js.Array[(Cotonoma, Coto)] =
    json.node_roots.map(pair =>
      (CotonomaBackend.toModel(pair._1), CotoBackend.toModel(pair._2))
    )

  def localNodeId: Id[Node] = Id(json.local_node_id)

  def localNode: Option[Node] = nodes.get(localNodeId)

  lazy val parentNodeIds: js.Array[Id[Node]] =
    json.parent_node_ids.map(Id[Node](_))

  lazy val servers: js.Array[Server] =
    json.servers.map(ServerBackend.toModel(_))

  def debug: String = {
    val s = new StringBuilder
    s ++= s"lastChangeNumber: ${lastChangeNumber}"
    s ++= s", nodes: ${nodes.size}"
    s ++= s", nodeRoots: ${nodeRoots.size}"
    s ++= s", localNode: {${localNode.map(_.debug)}}"
    s ++= s", parentNodes: ${parentNodeIds.size}"
    s ++= s", servers: ${servers.size}"
    s.result()
  }
}

object InitialDataset {
  def switchOperatingNodeTo(
      parentId: Option[Id[Node]]
  ): Cmd.Single[Either[ErrorJson, InitialDataset]] =
    InitialDatasetJson.switchOperatingNodeTo(parentId)
      .map(_.map(InitialDataset(_)))
}

@js.native
trait InitialDatasetJson extends js.Object {
  val last_change_number: Double = js.native
  val nodes: js.Array[NodeJson] = js.native
  val node_roots: js.Array[js.Tuple2[CotonomaJson, CotoJson]] = js.native
  val local_node_id: String = js.native
  val parent_node_ids: js.Array[String] = js.native
  val servers: js.Array[ServerJson] = js.native
}

object InitialDatasetJson {
  def switchOperatingNodeTo(
      parentId: Option[Id[Node]]
  ): Cmd.Single[Either[ErrorJson, InitialDatasetJson]] =
    tauri
      .invokeCommand(
        "operate_as",
        js.Dynamic
          .literal(
            parentId = parentId.map(_.uuid).getOrElse(null)
          )
      )
}
