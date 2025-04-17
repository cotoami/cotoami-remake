package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.libs.tauri

import cotoami.models.{ActiveClient, Id, LocalNode, Node, Server}

case class InitialDataset(json: InitialDatasetJson) {
  def lastChangeNumber: Double = json.last_change_number

  lazy val nodes: Map[Id[Node], Node] =
    json.nodes
      .map(NodeBackend.toModel)
      .map(node => node.id -> node)
      .toMap

  def localSettings: LocalNode = LocalNodeBackend.toModel(json.local_settings)

  def localNode: Option[Node] = nodes.get(localSettings.nodeId)

  lazy val parentNodeIds: js.Array[Id[Node]] =
    json.parent_node_ids.map(Id[Node](_))

  lazy val servers: js.Array[Server] =
    json.servers.map(ServerBackend.toModel)

  lazy val activeClients: js.Array[ActiveClient] =
    json.active_clients.map(ActiveClientBackend.toModel)
}

object InitialDataset {
  def switchSelfNodeTo(
      parentId: Option[Id[Node]]
  ): Cmd.One[Either[ErrorJson, InitialDataset]] =
    InitialDatasetJson.switchSelfNodeTo(parentId)
      .map(_.map(InitialDataset(_)))
}

@js.native
trait InitialDatasetJson extends js.Object {
  val last_change_number: Double = js.native
  val nodes: js.Array[NodeJson] = js.native
  val local_settings: LocalNodeJson = js.native
  val parent_node_ids: js.Array[String] = js.native
  val servers: js.Array[ServerJson] = js.native
  val active_clients: js.Array[ActiveClientJson] = js.native
}

object InitialDatasetJson {
  def switchSelfNodeTo(
      parentId: Option[Id[Node]]
  ): Cmd.One[Either[ErrorJson, InitialDatasetJson]] =
    tauri
      .invokeCommand(
        "operate_as",
        js.Dynamic.literal(
          parentId = parentId.map(_.uuid).getOrElse(null)
        )
      )
}
