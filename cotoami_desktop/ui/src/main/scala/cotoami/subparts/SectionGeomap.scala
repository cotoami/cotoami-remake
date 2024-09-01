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
    case class Init(bounds: GeoBounds) extends Msg
    case class LocationClicked(location: Geolocation) extends Msg
    case class ZoomChanged(zoom: Double) extends Msg
    case class CenterMoved(center: Geolocation) extends Msg
    case class BoundsChanged(bounds: GeoBounds) extends Msg
  }

  def update(msg: Msg, geomap: Geomap): (Geomap, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.Init(bounds) => {
        println(s"map init: ${bounds}")
        (geomap, Seq.empty)
      }

      case Msg.LocationClicked(location) =>
        (
          geomap.copy(focusedLocation = Some(location)),
          Seq.empty
        )

      case Msg.ZoomChanged(zoom) =>
        (geomap.copy(zoom = zoom), Seq.empty)

      case Msg.CenterMoved(center) =>
        (geomap.copy(center = center), Seq.empty)

      case Msg.BoundsChanged(bounds) => {
        println(s"bounds changed: ${bounds}")
        (geomap, Seq.empty)
      }
    }

  def apply(geomap: Geomap)(implicit dispatch: AppMsg => Unit): ReactElement =
    MapLibre(
      id = "main-geomap",
      center = geomap.center.toLngLat,
      zoom = geomap.zoom,
      applyCenterZoom = geomap.applyCenterZoom,
      focusedLocation = geomap.focusedLocation.map(_.toLngLat),
      onInit = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.Init(bounds).toApp)
      }),
      onClick = Some(e => {
        val location = Geolocation.fromMapLibre(e.lngLat)
        dispatch(Msg.LocationClicked(location).toApp)
      }),
      onZoomChanged = Some(zoom => dispatch(Msg.ZoomChanged(zoom).toApp)),
      onCenterMoved = Some(center => {
        val location = Geolocation.fromMapLibre(center)
        dispatch(Msg.CenterMoved(location).toApp)
      }),
      onBoundsChanged = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.BoundsChanged(bounds).toApp)
      })
    )
}
