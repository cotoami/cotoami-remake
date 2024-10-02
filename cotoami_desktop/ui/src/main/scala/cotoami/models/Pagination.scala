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
  def lastLoadedIndex: Option[Double]
  def totalItems: Double

  def totalPages: Double =
    if (pageSize == 0) 0
    else (totalItems / pageSize).ceil

  def nextPageIndex: Option[Double] =
    lastLoadedIndex match {
      case Some(i) => if ((i + 1) < totalPages) Some(i + 1) else None
      case None    => Some(0)
    }
}

case class PaginatedItems[T](
    items: Seq[T] = Seq.empty,
    pageSize: Double = 0,
    lastLoadedIndex: Option[Double] = None,
    totalItems: Double = 0
) extends Paginated {
  def isEmpty: Boolean = items.isEmpty

  def appendPage(page: Page[T]): PaginatedItems[T] = {
    val self =
      if (page.index == 0)
        // Reset values when adding the first page.
        PaginatedItems[T]()
      else if (page.index <= lastLoadedIndex.getOrElse(-1.0))
        // Do nothing if the page has been already appended.
        return this
      else
        this

    self.copy(
      items = self.items ++ page.items,
      pageSize = page.size,
      lastLoadedIndex = Some(page.index),
      totalItems = page.totalItems
    )
  }
}

case class PaginatedIds[T <: Entity[T]](
    ids: Set[Id[T]] = Set.empty[Id[T]],
    order: Seq[Id[T]] = Seq.empty,
    pageSize: Double = 0,
    lastLoadedIndex: Option[Double] = None,
    totalItems: Double = 0
) extends Paginated {
  def isEmpty: Boolean = ids.isEmpty

  def appendPage(page: Page[T]): PaginatedIds[T] = {
    val self =
      if (page.index == 0)
        // Reset values when adding the first page.
        PaginatedIds[T]()
      else if (page.index <= lastLoadedIndex.getOrElse(-1.0))
        // Do nothing if the page has been already appended.
        return this
      else
        this

    // The actual page position could be moved when inserts or deletes occur in
    // the database. For example, inserts could move the position backward and
    // as a result the page could contain IDs that have already been appended.
    // So it needs to filter the duplicate IDs. As for the opposite case (some 
    // missing entities in the page), we can do nothing about it here.
    val idsToAdd = page.items.map(_.id).filterNot(self.ids.contains)

    self.copy(
      ids = self.ids ++ idsToAdd,
      order = self.order ++ idsToAdd,
      pageSize = page.size,
      lastLoadedIndex = Some(page.index),
      totalItems = page.totalItems
    )
  }

  def prependId(id: Id[T]): PaginatedIds[T] =
    this
      .modify(_.order).using(order =>
        id +: (if (ids.contains(id))
                 order.filterNot(_ == id)
               else
                 order)
      )
      .modify(_.ids).using(_ + id)
      .modify(_.lastLoadedIndex).using(index =>
        if (pageSize > 0) {
          // recalculate the page index according to the size after prepending
          val pages = (ids.size / pageSize).floor
          if (pages > 0) Some(pages - 1) else None
        } else {
          index
        }
      )
}
