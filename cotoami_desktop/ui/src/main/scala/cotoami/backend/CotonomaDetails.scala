package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.{Coto, Cotonoma, Id}

case class CotonomaDetails(json: CotonomaDetailsJson) {
  def cotonoma: Cotonoma = CotonomaBackend.toModel(json.cotonoma)
  def coto: Coto = CotoBackend.toModel(json.coto)
  def supers: js.Array[Cotonoma] =
    json.supers.map(CotonomaBackend.toModel(_))
  def subs: Paginated[Cotonoma, _] =
    Paginated(json.subs, CotonomaBackend.toModel(_))

  def debug: String = {
    val s = new StringBuilder
    s ++= s"id: ${cotonoma.id}"
    s ++= s", cotoId: ${cotonoma.cotoId}"
    s ++= s", name: ${cotonoma.name}"
    s ++= s", supers: ${supers.size}"
    s ++= s", subs: {${subs.debug}}"
    s.result()
  }
}

object CotonomaDetails {
  def fetch(id: Id[Cotonoma]): Cmd.One[Either[ErrorJson, CotonomaDetails]] =
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
  def fetch(
      id: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, CotonomaDetailsJson]] =
    Commands.send(Commands.CotonomaDetails(id))
}
