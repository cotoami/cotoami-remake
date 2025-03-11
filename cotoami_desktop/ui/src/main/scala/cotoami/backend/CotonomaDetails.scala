package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{Coto, Cotonoma, Id, Page}

case class CotonomaDetails(json: CotonomaDetailsJson) {
  def cotonoma: Cotonoma = CotonomaBackend.toModel(json.cotonoma)
  def coto: Coto = CotoBackend.toModel(json.coto)
  def supers: js.Array[Cotonoma] =
    json.supers.map(CotonomaBackend.toModel(_))
  def subs: Page[Cotonoma] =
    PageBackend.toModel(json.subs, CotonomaBackend.toModel)
  def postCount: Double = json.post_count
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
  val subs: PageJson[CotonomaJson] = js.native
  val post_count: Double = js.native
}

object CotonomaDetailsJson {
  def fetch(
      id: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, CotonomaDetailsJson]] =
    Commands.send(Commands.CotonomaDetails(id))
}
