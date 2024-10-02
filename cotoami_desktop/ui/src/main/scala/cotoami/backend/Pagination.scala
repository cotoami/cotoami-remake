package cotoami.backend

import scala.scalajs.js

import cotoami.models.Page

@js.native
trait PageJson[T] extends js.Object {
  val rows: js.Array[T] = js.native
  val size: Double = js.native
  val index: Double = js.native
  val total_rows: Double = js.native
}

object PageBackend {
  def toModel[T, J](json: PageJson[J], map: J => T): Page[T] =
    Page(
      items = json.rows.map(map),
      size = json.size,
      index = json.index,
      totalItems = json.total_rows
    )
}
