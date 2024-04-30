package cotoami.backend

import scala.scalajs.js

case class PaginatedCotos(json: PaginatedCotosJson) {
  def page: Paginated[Coto, _] = Paginated(this.json.page, Coto)
  def relatedData: CotosRelatedData = CotosRelatedData(this.json.related_data)
}

@js.native
trait PaginatedCotosJson extends js.Object {
  val page: PaginatedJson[CotoJson] = js.native
  val related_data: CotosRelatedDataJson = js.native
}

object PaginatedCotosJson {
  def debug(cotos: PaginatedCotosJson): String = {
    val s = new StringBuilder
    s ++= s"cotos: {${PaginatedJson.debug(cotos.page)}}"
    s ++= s", related_data: {${CotosRelatedDataJson.debug(cotos.related_data)}}"
    s.result()
  }
}
