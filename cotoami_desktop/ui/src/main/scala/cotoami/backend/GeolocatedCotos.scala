package cotoami.backend

import scala.scalajs.js
import marubinotto.fui.Cmd

import cotoami.models.{Coto, GeoBounds, Scope}

case class GeolocatedCotos(json: GeolocatedCotosJson) {
  def cotos: js.Array[Coto] = json.cotos.map(CotoBackend.toModel(_))
  def relatedData: CotosRelatedData = CotosRelatedData(json.related_data)
}

object GeolocatedCotos {
  def fetch(
      scope: Scope
  ): Cmd.One[Either[ErrorJson, GeolocatedCotos]] =
    GeolocatedCotosJson.fetch(scope)
      .map(_.map(GeolocatedCotos(_)))

  def inGeoBounds(
      bounds: GeoBounds
  ): Cmd.One[Either[ErrorJson, GeolocatedCotos]] =
    GeolocatedCotosJson.inGeoBounds(bounds)
      .map(_.map(GeolocatedCotos(_)))
}

@js.native
trait GeolocatedCotosJson extends js.Object {
  val cotos: js.Array[CotoJson] = js.native
  val related_data: CotosRelatedDataJson = js.native
}

object GeolocatedCotosJson {
  def fetch(
      scope: Scope
  ): Cmd.One[Either[ErrorJson, GeolocatedCotosJson]] =
    Commands.send(Commands.GeolocatedCotos(scope))

  def inGeoBounds(
      bounds: GeoBounds
  ): Cmd.One[Either[ErrorJson, GeolocatedCotosJson]] =
    Commands.send(Commands.CotosInGeoBounds(bounds))
}
