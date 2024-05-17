package cotoami.backend

import scala.scalajs.js

case class Paginated[T <: Entity[T], J](json: PaginatedJson[J], map: J => T) {
  def rows: js.Array[T] = this.json.rows.map(this.map)
  def pageSize: Double = this.json.page_size
  def pageIndex: Double = this.json.page_index
  def totalRows: Double = this.json.total_rows
}

@js.native
trait PaginatedJson[T] extends js.Object {
  val rows: js.Array[T] = js.native
  val page_size: Double = js.native
  val page_index: Double = js.native
  val total_rows: Double = js.native
}

object PaginatedJson {
  def debug[T](paginated: PaginatedJson[T]): String = {
    val s = new StringBuilder
    s ++= s"page_index: ${paginated.page_index}"
    s ++= s", page_size: ${paginated.page_size}"
    s ++= s", total_rows: ${paginated.total_rows}"
    s.result()
  }
}

case class PaginatedIds[T <: Entity[T]](
    ids: Set[Id[T]] = Set.empty[Id[T]],
    order: Seq[Id[T]] = Seq.empty,
    pageSize: Double = 0,
    pageIndex: Option[Double] = None,
    total: Double = 0
) {
  def add(page: Paginated[T, _]): PaginatedIds[T] = {
    // Reset values when adding the first page (index == 0).
    val self =
      if (page.pageIndex == 0)
        PaginatedIds[T]()
      else
        this

    // Filter IDs that have already added to avoid duplicates.
    val idsToAdd = page.rows.map(_.id).filterNot(self.ids.contains)

    self.copy(
      ids = self.ids ++ idsToAdd,
      order = self.order ++ idsToAdd,
      pageSize = page.pageSize,
      pageIndex = Some(page.pageIndex),
      total = page.totalRows
    )
  }

  def nextPageIndex: Option[Double] =
    this.pageIndex match {
      case Some(i) => if ((i + 1) < this.totalPages) Some(i + 1) else None
      case None    => Some(0)
    }

  def totalPages: Double =
    if (this.pageSize == 0) 0
    else (this.total / this.pageSize).ceil

  def debug: String = {
    val s = new StringBuilder
    s ++= s"local: ${this.ids.size}"
    s ++= s", page: ${this.pageIndex} of ${this.totalPages}"
    s ++= s", total: ${this.total}"
    s.result()
  }
}
