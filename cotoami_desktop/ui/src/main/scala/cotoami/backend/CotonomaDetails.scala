package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.Id

case class CotonomaDetails(json: CotonomaDetailsJson) {
  def cotonoma: Cotonoma = Cotonoma(this.json.cotonoma)
  def coto: Coto = Coto(this.json.coto)
  def supers: js.Array[Cotonoma] = this.json.supers.map(Cotonoma(_))
  def subs: Paginated[Cotonoma, _] = Paginated(this.json.subs, Cotonoma(_))

  def debug: String = {
    val s = new StringBuilder
    s ++= s"id: ${this.cotonoma.id}"
    s ++= s", cotoId: ${this.cotonoma.cotoId}"
    s ++= s", name: ${this.cotonoma.name}"
    s ++= s", supers: ${this.supers.size}"
    s ++= s", subs: {${this.subs.debug}}"
    s.result()
  }
}

object CotonomaDetails {
  def fetch(id: Id[Cotonoma]): Cmd[Either[ErrorJson, CotonomaDetails]] =
    CotonomaDetailsJson.fetch(id).map(_.map(CotonomaDetails(_)))
}

@js.native
trait CotonomaDetailsJson extends js.Object {
  val cotonoma: CotonomaJson = js.native
  val coto: CotoJson = js.native
  val supers: js.Array[CotonomaJson] = js.native
  val subs: PaginatedJson[CotonomaJson] = js.native
}

object CotonomaDetailsJson {
  def fetch(id: Id[Cotonoma]): Cmd[Either[ErrorJson, CotonomaDetailsJson]] =
    Commands.send(Commands.CotonomaDetails(id))
}
