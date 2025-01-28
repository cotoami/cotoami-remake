package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{
  Coto,
  Cotonoma,
  DateTimeRange,
  Geolocation,
  Id,
  Node,
  Page
}

@js.native
trait CotonomaJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val coto_id: String = js.native
  val name: String = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
}

object CotonomaJson {
  def fetch(
      id: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.Cotonoma(id))

  def fetchByName(
      name: String,
      nodeId: Id[Node]
  ): Cmd.One[Either[ErrorJson, CotonomaJson]] =
    Commands.send(Commands.CotonomaByName(name, nodeId))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PageJson[CotonomaJson]]] =
    Commands.send(Commands.RecentCotonomas(nodeId, pageIndex))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PageJson[CotonomaJson]]] =
    Commands.send(Commands.SubCotonomas(id, pageIndex))

  def fetchByPrefix(
      prefix: String,
      nodes: Option[js.Array[Id[Node]]]
  ): Cmd.One[Either[ErrorJson, js.Array[CotonomaJson]]] =
    Commands.send(Commands.CotonomasByPrefix(prefix, nodes))

  def post(
      name: String,
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.PostCotonoma(name, location, timeRange, postTo))

  def rename(
      id: Id[Cotonoma],
      name: String
  ): Cmd.One[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.RenameCotonoma(id, name))
}

object CotonomaBackend {
  def toModel(json: CotonomaJson): Cotonoma =
    Cotonoma(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      cotoId = Id(json.coto_id),
      name = json.name,
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at
    )

  def fetch(id: Id[Cotonoma]): Cmd.One[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.fetch(id)
      .map(_.map(pair => (toModel(pair._1), CotoBackend.toModel(pair._2))))

  def fetchByName(
      name: String,
      nodeId: Id[Node]
  ): Cmd.One[Either[ErrorJson, Cotonoma]] =
    CotonomaJson.fetchByName(name, nodeId).map(_.map(toModel))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, Page[Cotonoma]]] =
    CotonomaJson.fetchRecent(nodeId, pageIndex)
      .map(_.map(PageBackend.toModel(_, toModel)))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, Page[Cotonoma]]] =
    CotonomaJson.fetchSubs(id, pageIndex)
      .map(_.map(PageBackend.toModel(_, toModel)))

  def fetchByPrefix(
      prefix: String,
      nodes: Option[js.Array[Id[Node]]]
  ): Cmd.One[Either[ErrorJson, js.Array[Cotonoma]]] =
    CotonomaJson.fetchByPrefix(prefix, nodes)
      .map(_.map(_.map(toModel)))

  def post(
      name: String,
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.post(name, location, timeRange, postTo)
      .map(_.map(pair => (toModel(pair._1), CotoBackend.toModel(pair._2))))

  def rename(
      id: Id[Cotonoma],
      name: String
  ): Cmd.One[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.rename(id, name)
      .map(_.map(pair => (toModel(pair._1), CotoBackend.toModel(pair._2))))
}
