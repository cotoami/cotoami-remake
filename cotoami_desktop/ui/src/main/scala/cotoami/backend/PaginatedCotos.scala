package cotoami.backend

import scala.scalajs.js
import marubinotto.fui.Cmd

import cotoami.models.{Coto, Cotonoma, Id, Ito, Node, Page}

case class PaginatedCotos(json: PaginatedCotosJson) {
  def page: Page[Coto] =
    PageBackend.toModel(json.page, CotoBackend.toModel)
  def relatedData: CotosRelatedData = CotosRelatedData(json.related_data)
  def outgoingItos: js.Array[Ito] =
    json.outgoing_itos.map(ItoBackend.toModel)
}

object PaginatedCotos {
  def fetchRecent(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.fetchRecent(nodeId, cotonomaId, onlyCotonomas, pageIndex)
      .map(_.map(PaginatedCotos(_)))

  def search(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.search(
      query,
      nodeId,
      cotonomaId,
      onlyCotonomas,
      pageIndex
    )
      .map(_.map(PaginatedCotos(_)))
}

@js.native
trait PaginatedCotosJson extends js.Object {
  val page: PageJson[CotoJson] = js.native
  val related_data: CotosRelatedDataJson = js.native
  val outgoing_itos: js.Array[ItoJson] = js.native
}

object PaginatedCotosJson {
  def fetchRecent(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(
      Commands.RecentCotos(nodeId, cotonomaId, onlyCotonomas, pageIndex)
    )

  def search(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(
      Commands.SearchCotos(query, nodeId, cotonomaId, onlyCotonomas, pageIndex)
    )
}
