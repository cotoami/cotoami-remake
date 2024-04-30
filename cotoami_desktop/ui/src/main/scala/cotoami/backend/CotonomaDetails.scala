package cotoami.backend

import scala.scalajs.js

case class CotonomaDetails(json: CotonomaDetailsJson) {
  def cotonoma: Cotonoma = Cotonoma(this.json.cotonoma)
  def coto: Coto = Coto(this.json.coto)
  def supers: js.Array[Cotonoma] = this.json.supers.map(Cotonoma(_))
  def subs: Paginated[Cotonoma, _] = Paginated(this.json.subs, Cotonoma(_))
}

@js.native
trait CotonomaDetailsJson extends js.Object {
  val cotonoma: CotonomaJson = js.native
  val coto: CotoJson = js.native
  val supers: js.Array[CotonomaJson] = js.native
  val subs: PaginatedJson[CotonomaJson] = js.native
}

object CotonomaDetailsJson {
  def debug(details: CotonomaDetailsJson): String = {
    val s = new StringBuilder
    s ++= s"id: ${details.cotonoma.uuid}"
    s ++= s", coto_id: ${details.cotonoma.coto_id}"
    s ++= s", name: ${details.cotonoma.name}"
    s ++= s", supers: ${details.supers.size}"
    s ++= s", subs: {${PaginatedJson.debug(details.subs)}}"
    s.result()
  }
}
