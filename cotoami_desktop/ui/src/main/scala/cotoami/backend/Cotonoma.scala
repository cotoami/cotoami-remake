package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*
import scala.scalajs.js

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
  ): IO[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.Cotonoma(id))

  def fetchByCotoId(
      id: Id[Coto]
  ): IO[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.CotonomaByCotoId(id))

  def fetchByName(
      name: String,
      nodeId: Id[Node]
  ): IO[Either[ErrorJson, CotonomaJson]] =
    Commands.send(Commands.CotonomaByName(name, nodeId))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): IO[Either[ErrorJson, PageJson[CotonomaJson]]] =
    Commands.send(Commands.RecentCotonomas(nodeId, pageIndex))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): IO[Either[ErrorJson, PageJson[CotonomaJson]]] =
    Commands.send(Commands.SubCotonomas(id, pageIndex))

  def fetchByPrefix(
      prefix: String,
      nodes: Option[js.Array[Id[Node]]]
  ): IO[Either[ErrorJson, js.Array[CotonomaJson]]] =
    Commands.send(Commands.CotonomasByPrefix(prefix, nodes))

  def fetchByPartial(
      partial: String,
      nodes: Option[js.Array[Id[Node]]]
  ): IO[Either[ErrorJson, js.Array[CotonomaJson]]] =
    Commands.send(Commands.CotonomasByPartial(partial, nodes))

  def post(
      name: String,
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): IO[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.PostCotonoma(name, location, timeRange, postTo))

  def rename(
      id: Id[Cotonoma],
      name: String
  ): IO[Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]] =
    Commands.send(Commands.RenameCotonoma(id, name))
}

object CotonomaBackend {
  def decodePair(
      pair: js.Tuple2[CotonomaJson, CotoJson]
  ): IO[(Cotonoma, Coto)] =
    CotoBackend.toModel(pair._2).map(coto => (toModel(pair._1), coto))

  def toModel(json: CotonomaJson): Cotonoma =
    Cotonoma(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      cotoId = Id(json.coto_id),
      name = json.name,
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at
    )

  def fetch(id: Id[Cotonoma]): IO[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.fetch(id)
      .flatMap(_.traverse(decodePair))

  def fetchByCotoId(
      id: Id[Coto]
  ): IO[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.fetchByCotoId(id)
      .flatMap(_.traverse(decodePair))

  def fetchByName(
      name: String,
      nodeId: Id[Node]
  ): IO[Either[ErrorJson, Cotonoma]] =
    CotonomaJson.fetchByName(name, nodeId).map(_.map(toModel))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): IO[Either[ErrorJson, Page[Cotonoma]]] =
    CotonomaJson.fetchRecent(nodeId, pageIndex)
      .map(_.map(PageBackend.toModel(_, toModel)))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): IO[Either[ErrorJson, Page[Cotonoma]]] =
    CotonomaJson.fetchSubs(id, pageIndex)
      .map(_.map(PageBackend.toModel(_, toModel)))

  def fetchByPrefix(
      prefix: String,
      nodes: Option[js.Array[Id[Node]]]
  ): IO[Either[ErrorJson, js.Array[Cotonoma]]] =
    CotonomaJson.fetchByPrefix(prefix, nodes)
      .map(_.map(_.map(toModel)))

  def fetchByPartial(
      partial: String,
      nodes: Option[js.Array[Id[Node]]]
  ): IO[Either[ErrorJson, js.Array[Cotonoma]]] =
    CotonomaJson.fetchByPartial(partial, nodes)
      .map(_.map(_.map(toModel)))

  def post(
      name: String,
      location: Option[Geolocation],
      timeRange: Option[DateTimeRange],
      postTo: Id[Cotonoma]
  ): IO[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.post(name, location, timeRange, postTo)
      .flatMap(_.traverse(decodePair))

  def rename(
      id: Id[Cotonoma],
      name: String
  ): IO[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.rename(id, name)
      .flatMap(_.traverse(decodePair))
}
