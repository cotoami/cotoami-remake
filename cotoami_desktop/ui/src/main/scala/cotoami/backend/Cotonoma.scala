package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{Coto, Cotonoma, Geolocation, Id, Node}

@js.native
trait CotonomaJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val coto_id: String = js.native
  val name: String = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
  val posts: Int = js.native
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
  ): Cmd.One[Either[ErrorJson, PaginatedJson[CotonomaJson]]] =
    Commands.send(Commands.RecentCotonomas(nodeId, pageIndex))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedJson[CotonomaJson]]] =
    Commands.send(Commands.SubCotonomas(id, pageIndex))

  def post(
      name: String,
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.PostCotonoma(name, location, postTo))
}

object CotonomaBackend {
  def toModel(json: CotonomaJson): Cotonoma =
    Cotonoma(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      cotoId = Id(json.coto_id),
      name = json.name,
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at,
      posts = json.posts
    )

  def fetch(id: Id[Cotonoma]): Cmd.One[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.fetch(id)
      .map(_.map(pair => (toModel(pair._1), CotoBackend.toModel(pair._2))))

  def fetchByName(
      name: String,
      nodeId: Id[Node]
  ): Cmd.One[Either[ErrorJson, Cotonoma]] =
    CotonomaJson.fetchByName(name, nodeId).map(_.map(toModel(_)))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    CotonomaJson.fetchRecent(nodeId, pageIndex)
      .map(_.map(Paginated(_, toModel(_))))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    CotonomaJson.fetchSubs(id, pageIndex)
      .map(_.map(Paginated(_, toModel(_))))

  def post(
      name: String,
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.post(name, location, postTo)
      .map(_.map(pair => (toModel(pair._1), CotoBackend.toModel(pair._2))))
}
