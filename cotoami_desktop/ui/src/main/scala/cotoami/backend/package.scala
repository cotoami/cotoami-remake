package cotoami

import scala.scalajs.js
import java.time._

package object backend {

  case class Id[T](uuid: String) extends AnyVal

  @js.native
  trait Paginated[T] extends js.Object {
    val rows: js.Array[T] = js.native
    val page_size: Double = js.native
    val page_index: Double = js.native
    val total_rows: Double = js.native
  }

  case class PaginatedIds[T](
      ids: Set[Id[T]] = Set.empty[Id[T]],
      order: Seq[Id[T]] = Seq.empty,
      pageSize: Double = 0,
      pageIndex: Option[Double] = None,
      total: Double = 0
  ) {
    def addPage[S](page: Paginated[S], toId: S => Id[T]): PaginatedIds[T] = {
      val idsToAdd = page.rows.map(toId).filterNot(this.ids.contains)
      this.copy(
        ids = this.ids ++ idsToAdd,
        order = this.order ++ idsToAdd,
        pageSize = page.page_size,
        pageIndex = Some(page.page_index),
        total = page.total_rows
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

  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")
}
