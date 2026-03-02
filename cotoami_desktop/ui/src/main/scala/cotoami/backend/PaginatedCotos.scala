package cotoami.backend

import scala.scalajs.js
import marubinotto.fui.Cmd

import cotoami.models.{Coto, Ito, Page, Scope}

case class PaginatedCotos(json: PaginatedCotosJson) {
  def page: Page[Coto] =
    PageBackend.toModel(json.page, CotoBackend.toModel)
  def relatedData: CotosRelatedData = CotosRelatedData(json.related_data)
  def outgoingItos: js.Array[Ito] =
    json.outgoing_itos.map(ItoBackend.toModel)
}

object PaginatedCotos {
  def fetchRecent(
      scope: Scope,
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.fetchRecent(scope, onlyCotonomas, pageIndex)
      .map(_.map(PaginatedCotos(_)))

  def search(
      query: String,
      scope: Scope,
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.search(
      query,
      scope,
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
      scope: Scope,
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(
      Commands.RecentCotos(scope, onlyCotonomas, pageIndex)
    )

  def search(
      query: String,
      scope: Scope,
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(
      Commands.SearchCotos(query, scope, onlyCotonomas, pageIndex)
    )
}
