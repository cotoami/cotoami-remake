package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.{GeoBounds, Geolocation, Id, Node}

case class GeolocatedCotos(json: GeolocatedCotosJson) {
  def cotos: js.Array[Coto] = this.json.cotos.map(Coto(_))
  def relatedData: CotosRelatedData = CotosRelatedData(this.json.related_data)

  def geoBounds: Option[Either[Geolocation, GeoBounds]] = {
    this.cotos.toSeq match {
      case Seq()     => None
      case Seq(coto) => coto.geolocation.map(Left(_))
      case cotos => {
        val locations = cotos.flatMap(_.geolocation)
        val bounds = GeoBounds(
          southwest = Geolocation(
            longitude = locations.map(_.longitude).min,
            latitude = locations.map(_.latitude).min
          ),
          northeast = Geolocation(
            longitude = locations.map(_.longitude).max,
            latitude = locations.map(_.latitude).max
          )
        )
        Some(Right(bounds))
      }
    }
  }

  def debug: String = {
    val s = new StringBuilder
    s ++= s"coto count: ${this.cotos.size}"
    s ++= s", relatedData: {${this.relatedData.debug}}"
    s.result()
  }
}

object GeolocatedCotos {
  def fetch(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]]
  ): Cmd[Either[ErrorJson, GeolocatedCotos]] =
    GeolocatedCotosJson.fetch(nodeId, cotonomaId)
      .map(_.map(GeolocatedCotos(_)))

  def inGeoBounds(bounds: GeoBounds): Cmd[Either[ErrorJson, GeolocatedCotos]] =
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
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]]
  ): Cmd[Either[ErrorJson, GeolocatedCotosJson]] =
    Commands.send(Commands.GeolocatedCotos(nodeId, cotonomaId))

  def inGeoBounds(
      bounds: GeoBounds
  ): Cmd[Either[ErrorJson, GeolocatedCotosJson]] =
    Commands.send(Commands.CotosInGeoBounds(bounds))
}
