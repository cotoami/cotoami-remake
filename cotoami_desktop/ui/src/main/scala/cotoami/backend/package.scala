package cotoami

import scala.scalajs.js
import java.time._

import fui.FunctionalUI.Cmd
import cotoami.{Id, Log, Validation}

package object backend {

  @js.native
  trait Error extends js.Object {
    val code: String = js.native
    val message: String = js.native
    val details: String = js.native
  }

  object Error {
    def toValidationError(error: Error): Validation.Error =
      Validation.Error(error.code, error.message)

    def log(error: Error, message: String): Cmd[cotoami.Msg] =
      cotoami.log_error(message, Some(js.JSON.stringify(error)))
  }

  @js.native
  trait LogEvent extends js.Object {
    val level: String = js.native
    val message: String = js.native
    val details: String = js.native
  }

  object LogEvent {
    def toLogEntry(event: LogEvent): Log.Entry =
      Log.Entry(
        Log.levels.get(event.level).getOrElse(Log.Debug),
        event.message,
        Option(event.details)
      )
  }

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
