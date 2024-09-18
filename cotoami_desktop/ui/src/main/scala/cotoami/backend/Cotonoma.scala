package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{Cotonoma, Geolocation, Id, Node}

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
  ): Cmd[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.Cotonoma(id))

  def fetchByName(
      name: String,
      nodeId: Id[Node]
  ): Cmd[Either[ErrorJson, CotonomaJson]] =
    Commands.send(Commands.CotonomaByName(name, nodeId))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, PaginatedJson[CotonomaJson]]] =
    Commands.send(Commands.RecentCotonomas(nodeId, pageIndex))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, PaginatedJson[CotonomaJson]]] =
    Commands.send(Commands.SubCotonomas(id, pageIndex))

  def post(
      name: String,
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.PostCotonoma(name, location, postTo))
}
