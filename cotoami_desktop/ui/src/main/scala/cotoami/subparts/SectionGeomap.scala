package cotoami.subparts

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.components.MapLibre
import cotoami.models.{Geolocation, Geomap}

object SectionGeomap {

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.SectionGeomapMsg(this)
  }

  object Msg {
    case object MapInit extends Msg
    case class MapClicked(location: Geolocation) extends Msg
    case class MapZoomChanged(zoom: Double) extends Msg
    case class MapCenterMoved(center: Geolocation) extends Msg
  }

  def update(msg: Msg, geomap: Geomap): (Geomap, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.MapInit => (geomap, Seq.empty)

      case Msg.MapClicked(location) =>
        (
          geomap.copy(focusedLocation = Some(location)),
          Seq.empty
        )

      case Msg.MapZoomChanged(zoom) =>
        (geomap.copy(zoom = zoom), Seq.empty)

      case Msg.MapCenterMoved(center) =>
        (geomap.copy(center = center), Seq.empty)
    }

  def apply(geomap: Geomap)(implicit dispatch: AppMsg => Unit): ReactElement =
    MapLibre(
      id = "main-geomap",
      center = toLngLat(geomap.center),
      zoom = geomap.zoom,
      syncCenterZoom = geomap.syncCenterZoom,
      focusedLocation = geomap.focusedLocation.map(toLngLat),
      onInit = Some(() => dispatch(Msg.MapInit.toApp)),
      onClick = Some(e => {
        val location = Geolocation.fromLngLat(e.lngLat.toArray())
        dispatch(Msg.MapClicked(location).toApp)
      }),
      onZoomChanged = Some(zoom => dispatch(Msg.MapZoomChanged(zoom).toApp)),
      onCenterMoved = Some(center =>
        dispatch(Msg.MapCenterMoved(Geolocation.fromLngLat(center)).toApp)
      )
    )

  private def toLngLat(location: Geolocation): (Double, Double) =
    (location.longitude, location.latitude)
}
