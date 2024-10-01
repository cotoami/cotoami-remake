package cotoami.backend

import scala.scalajs.js

import cotoami.models.Paginated

@js.native
trait PaginatedJson[T] extends js.Object {
  val rows: js.Array[T] = js.native
  val page_size: Double = js.native
  val page_index: Double = js.native
  val total_rows: Double = js.native
}

object PaginatedBackend {
  def toModel[T, J](json: PaginatedJson[J], map: J => T): Paginated[T] =
    Paginated(
      rows = json.rows.map(map),
      pageSize = json.page_size,
      pageIndex = json.page_index,
      totalRows = json.total_rows
    )
}
