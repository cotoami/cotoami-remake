package cotoami.subparts

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.components.MapLibre
import cotoami.models.{GeoBounds, Geolocation, Geomap}

object SectionGeomap {

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.SectionGeomapMsg(this)
  }

  object Msg {
    case class MapInit(bounds: GeoBounds) extends Msg
    case class MapClicked(location: Geolocation) extends Msg
    case class MapZoomChanged(zoom: Double) extends Msg
    case class MapCenterMoved(center: Geolocation) extends Msg
    case class MapBoundsChanged(bounds: GeoBounds) extends Msg
  }

  def update(msg: Msg, geomap: Geomap): (Geomap, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.MapInit(bounds) => {
        println(s"map init: ${bounds}")
        (geomap, Seq.empty)
      }

      case Msg.MapClicked(location) =>
        (
          geomap.copy(focusedLocation = Some(location)),
          Seq.empty
        )

      case Msg.MapZoomChanged(zoom) =>
        (geomap.copy(zoom = zoom), Seq.empty)

      case Msg.MapCenterMoved(center) =>
        (geomap.copy(center = center), Seq.empty)

      case Msg.MapBoundsChanged(bounds) => {
        println(s"bounds changed: ${bounds}")
        (geomap, Seq.empty)
      }
    }

  def apply(geomap: Geomap)(implicit dispatch: AppMsg => Unit): ReactElement =
    MapLibre(
      id = "main-geomap",
      center = geomap.center.toLngLat,
      zoom = geomap.zoom,
      syncCenterZoom = geomap.syncCenterZoom,
      focusedLocation = geomap.focusedLocation.map(_.toLngLat),
      onInit = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.MapInit(bounds).toApp)
      }),
      onClick = Some(e => {
        val location = Geolocation.fromMapLibre(e.lngLat)
        dispatch(Msg.MapClicked(location).toApp)
      }),
      onZoomChanged = Some(zoom => dispatch(Msg.MapZoomChanged(zoom).toApp)),
      onCenterMoved = Some(center => {
        val location = Geolocation.fromMapLibre(center)
        dispatch(Msg.MapCenterMoved(location).toApp)
      }),
      onBoundsChanged = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.MapBoundsChanged(bounds).toApp)
      })
    )
}
