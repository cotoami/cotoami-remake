package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import cotoami.models.{Coto, Cotonoma, Id, Page}

case class CotonomaDetails(json: CotonomaDetailsJson, coto: Coto) {
  def cotonoma: Cotonoma = CotonomaBackend.toModel(json.cotonoma)
  def supers: js.Array[Cotonoma] =
    json.supers.map(CotonomaBackend.toModel(_))
  def subs: Page[Cotonoma] =
    PageBackend.toModel(json.subs, CotonomaBackend.toModel)
  def postCount: Double = json.post_count
}

object CotonomaDetails {
  def decode(json: CotonomaDetailsJson): IO[CotonomaDetails] =
    for {
      coto <- CotoBackend.toModel(json.coto)
    } yield CotonomaDetails(json, coto)

  def fetch(id: Id[Cotonoma]): IO[Either[ErrorJson, CotonomaDetails]] =
    CotonomaDetailsJson.fetch(id).flatMap(_.traverse(CotonomaDetails.decode))
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
  ): IO[Either[ErrorJson, CotonomaDetailsJson]] =
    Commands.send(Commands.CotonomaDetails(id))
}
