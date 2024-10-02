package cotoami.models

import scala.scalajs.js
import com.softwaremill.quicklens._

case class Page[T](
    items: js.Array[T],
    size: Double,
    index: Double,
    totalItems: Double
)

trait Paginated {
  def pageSize: Double
  def pageIndex: Option[Double]
  def totalItems: Double

  def totalPages: Double =
    if (pageSize == 0) 0
    else (totalItems / pageSize).ceil

  def nextPageIndex: Option[Double] =
    pageIndex match {
      case Some(i) => if ((i + 1) < totalPages) Some(i + 1) else None
      case None    => Some(0)
    }
}

case class PaginatedIds[T <: Entity[T]](
    ids: Set[Id[T]] = Set.empty[Id[T]],
    order: Seq[Id[T]] = Seq.empty,
    pageSize: Double = 0,
    pageIndex: Option[Double] = None,
    totalItems: Double = 0
) extends Paginated {
  def isEmpty: Boolean = this.ids.isEmpty

  def appendPage(page: Page[T]): PaginatedIds[T] = {
    // Reset values when adding the first page (index == 0).
    val self =
      if (page.index == 0)
        PaginatedIds[T]()
      else
        this

    // Filter IDs that have already added to avoid duplicates.
    val idsToAdd = page.items.map(_.id).filterNot(self.ids.contains)

    self.copy(
      ids = self.ids ++ idsToAdd,
      order = self.order ++ idsToAdd,
      pageSize = page.size,
      pageIndex = Some(page.index),
      totalItems = page.totalItems
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
}
