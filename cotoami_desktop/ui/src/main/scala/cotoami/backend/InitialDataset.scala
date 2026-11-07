package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import marubinotto.libs.tauri

import cotoami.models.{ActiveClient, Id, LocalNode, Node, ParentNode, Server}

case class InitialDataset(
    json: InitialDatasetJson,
    nodes: Map[Id[Node], Node]
) {
  def lastChangeNumber: Double = json.last_change_number

  def localSettings: LocalNode = LocalNodeBackend.toModel(json.local_settings)

  def localNode: Option[Node] = nodes.get(localSettings.nodeId)

  lazy val parents: js.Array[ParentNode] =
    json.parents.map(ParentNodeBackend.toModel)

  lazy val servers: js.Array[Server] =
    json.servers.map(ServerBackend.toModel)

  lazy val activeClients: js.Array[ActiveClient] =
    json.active_clients.map(ActiveClientBackend.toModel)
}

object InitialDataset {
  def decode(json: InitialDatasetJson): IO[InitialDataset] =
    for {
      nodes <- decodeArray(json.nodes)(NodeBackend.toModel).map(
        _.map(node => node.id -> node).toMap
      )
    } yield InitialDataset(json, nodes)

  def switchSelfNodeTo(
      parentId: Option[Id[Node]]
  ): IO[Either[ErrorJson, InitialDataset]] =
    InitialDatasetJson.switchSelfNodeTo(parentId)
      .flatMap(_.traverse(InitialDataset.decode))
}

@js.native
trait InitialDatasetJson extends js.Object {
  val last_change_number: Double = js.native
  val nodes: js.Array[NodeJson] = js.native
  val local_settings: LocalNodeJson = js.native
  val parents: js.Array[ParentNodeJson] = js.native
  val servers: js.Array[ServerJson] = js.native
  val active_clients: js.Array[ActiveClientJson] = js.native
}

object InitialDatasetJson {
  def switchSelfNodeTo(
      parentId: Option[Id[Node]]
  ): IO[Either[ErrorJson, InitialDatasetJson]] =
    tauri
      .invokeCommandIO(
        "operate_as",
        js.Dynamic.literal(
          parentId = parentId.map(_.uuid).getOrElse(null)
        )
      )
}
