package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import cotoami.models.{Coto, GeoBounds, Scope}

case class GeolocatedCotos(
    json: GeolocatedCotosJson,
    cotos: js.Array[Coto],
    relatedData: CotosRelatedData
)

object GeolocatedCotos {
  def decode(json: GeolocatedCotosJson): IO[GeolocatedCotos] =
    for {
      cotos <- decodeArray(json.cotos)(CotoBackend.toModel)
      relatedData <- CotosRelatedData.decode(json.related_data)
    } yield GeolocatedCotos(json, cotos, relatedData)

  def fetch(
      scope: Scope
  ): IO[Either[ErrorJson, GeolocatedCotos]] =
    GeolocatedCotosJson.fetch(scope)
      .flatMap(_.traverse(GeolocatedCotos.decode))

  def inGeoBounds(
      bounds: GeoBounds
  ): IO[Either[ErrorJson, GeolocatedCotos]] =
    GeolocatedCotosJson.inGeoBounds(bounds)
      .flatMap(_.traverse(GeolocatedCotos.decode))
}

@js.native
trait GeolocatedCotosJson extends js.Object {
  val cotos: js.Array[CotoJson] = js.native
  val related_data: CotosRelatedDataJson = js.native
}

object GeolocatedCotosJson {
  def fetch(
      scope: Scope
  ): IO[Either[ErrorJson, GeolocatedCotosJson]] =
    Commands.send(Commands.GeolocatedCotos(scope))

  def inGeoBounds(
      bounds: GeoBounds
  ): IO[Either[ErrorJson, GeolocatedCotosJson]] =
    Commands.send(Commands.CotosInGeoBounds(bounds))
}
