package cotoami.backend

import scala.scalajs.js
import fui.Cmd

case class GeolocatedCotos(json: GeolocatedCotosJson) {
  def cotos: js.Array[Coto] = this.json.cotos.map(Coto(_))
  def relatedData: CotosRelatedData = CotosRelatedData(this.json.related_data)

  def debug: String = {
    val s = new StringBuilder
    s ++= s"coto count: {${this.cotos.size}}"
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
}
