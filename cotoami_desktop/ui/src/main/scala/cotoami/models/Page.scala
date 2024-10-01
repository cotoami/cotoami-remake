package cotoami.models

import scala.scalajs.js
import com.softwaremill.quicklens._

case class Page[T](
    rows: js.Array[T],
    size: Double,
    index: Double,
    totalRows: Double
)

case class PaginatedIds[T <: Entity[T]](
    ids: Set[Id[T]] = Set.empty[Id[T]],
    order: Seq[Id[T]] = Seq.empty,
    pageSize: Double = 0,
    pageIndex: Option[Double] = None,
    total: Double = 0
) {
  def isEmpty: Boolean = this.ids.isEmpty

  def appendPage(page: Page[T]): PaginatedIds[T] = {
    // Reset values when adding the first page (index == 0).
    val self =
      if (page.index == 0)
        PaginatedIds[T]()
      else
        this

    // Filter IDs that have already added to avoid duplicates.
    val idsToAdd = page.rows.map(_.id).filterNot(self.ids.contains)

    self.copy(
      ids = self.ids ++ idsToAdd,
      order = self.order ++ idsToAdd,
      pageSize = page.size,
      pageIndex = Some(page.index),
      total = page.totalRows
    )
  }

  def prependId(id: Id[T]): PaginatedIds[T] =
    this
      .modify(_.order).using(order =>
        id +: (if (this.ids.contains(id))
                 order.filterNot(_ == id)
               else
                 order)
      )
      .modify(_.ids).using(_ + id)
      .modify(_.pageIndex).using(index =>
        if (this.pageSize > 0) {
          // recalculate the page index according to the size after prepending
          val pages = (this.ids.size / this.pageSize).floor
          if (pages > 0) Some(pages - 1) else None
        } else {
          index
        }
      )

  def nextPageIndex: Option[Double] =
    this.pageIndex match {
      case Some(i) => if ((i + 1) < this.totalPages) Some(i + 1) else None
      case None    => Some(0)
    }

  def totalPages: Double =
    if (this.pageSize == 0) 0
    else (this.total / this.pageSize).ceil
}
