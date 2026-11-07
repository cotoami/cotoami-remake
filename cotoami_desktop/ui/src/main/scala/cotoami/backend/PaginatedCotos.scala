package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import cotoami.models.{Coto, Ito, Page, Scope}

case class PaginatedCotos(
    json: PaginatedCotosJson,
    page: Page[Coto],
    relatedData: CotosRelatedData
) {
  def outgoingItos: js.Array[Ito] =
    json.outgoing_itos.map(ItoBackend.toModel)
}

object PaginatedCotos {
  def decode(json: PaginatedCotosJson): IO[PaginatedCotos] =
    for {
      page <- decodeArray(json.page.rows)(CotoBackend.toModel).map(rows =>
        Page(
          items = rows,
          size = json.page.size,
          index = json.page.index,
          totalItems = json.page.total_rows
        )
      )
      relatedData <- CotosRelatedData.decode(json.related_data)
    } yield PaginatedCotos(json, page, relatedData)

  def fetchRecent(
      scope: Scope,
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): IO[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.fetchRecent(scope, onlyCotonomas, pageIndex)
      .flatMap(_.traverse(PaginatedCotos.decode))

  def search(
      query: String,
      scope: Scope,
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): IO[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.search(
      query,
      scope,
      onlyCotonomas,
      pageIndex
    )
      .flatMap(_.traverse(PaginatedCotos.decode))
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
  ): IO[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(
      Commands.RecentCotos(scope, onlyCotonomas, pageIndex)
    )

  def search(
      query: String,
      scope: Scope,
      onlyCotonomas: Boolean,
      pageIndex: Double
  ): IO[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(
      Commands.SearchCotos(query, scope, onlyCotonomas, pageIndex)
    )
}
