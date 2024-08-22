package cotoami.subparts

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.libs.geomap.maplibre
import cotoami.components.MapLibre
import cotoami.models.{Geolocation, Geomap}

object SectionGeomap {

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.SectionGeomapMsg(this)
  }

  object Msg {
    case object MapInit extends Msg
    case class MapClicked(lngLat: maplibre.LngLat) extends Msg
  }

  def update(msg: Msg, geomap: Geomap): (Geomap, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.MapInit =>
        (
          geomap.copy(forceSync = geomap.forceSync + 1),
          Seq.empty
        )

      case Msg.MapClicked(lngLat) =>
        (
          geomap.copy(focusedLocation =
            Some(Geolocation.fromLngLat(lngLat.toArray()))
          ),
          Seq.empty
        )
    }

  def apply(geomap: Geomap)(implicit dispatch: AppMsg => Unit): ReactElement =
    MapLibre(
      id = "main-geomap",
      center = toLngLat(geomap.center),
      zoom = geomap.zoom,
      focusedLocation = geomap.focusedLocation.map(toLngLat),
      forceSync = geomap.forceSync,
      onInit = Some(() => dispatch(Msg.MapInit.toApp)),
      onClick = Some(e => dispatch(Msg.MapClicked(e.lngLat).toApp))
    )

  private def toLngLat(location: Geolocation): (Double, Double) =
    (location.longitude, location.latitude)
}
