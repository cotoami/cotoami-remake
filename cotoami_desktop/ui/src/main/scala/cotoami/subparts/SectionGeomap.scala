package cotoami.subparts

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Context, Msg => AppMsg}
import cotoami.backend.{ErrorJson, GeolocatedCotos}
import cotoami.components.MapLibre
import cotoami.repositories.Domain
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
    case class GeolocatedCotosFetched(
        result: Either[ErrorJson, GeolocatedCotos]
    ) extends Msg
  }

  def update(msg: Msg, geomap: Geomap)(implicit
      context: Context
  ): (Geomap, Domain, Seq[Cmd[AppMsg]]) = {
    val default = (geomap, context.domain, Seq.empty)
    msg match {
      case Msg.Init(bounds) => default

      case Msg.LocationClicked(location) =>
        default.copy(_1 = geomap.copy(focusedLocation = Some(location)))

      case Msg.ZoomChanged(zoom) =>
        default.copy(_1 = geomap.copy(zoom = zoom))

      case Msg.CenterMoved(center) =>
        default.copy(_1 = geomap.copy(center = center))

      case Msg.BoundsChanged(bounds) => {
        println(s"bounds changed: ${bounds}")
        default
      }

      case Msg.GeolocatedCotosFetched(Right(cotos)) => {
        val center = context.domain.currentCotonomaCoto.flatMap(_.geolocation)
        default.copy(
          _1 = cotos.geoBounds match {
            case Some(Right(bounds))  => geomap
            case Some(Left(location)) => geomap
            case None => center.map(geomap.moveTo(_)).getOrElse(geomap)
          },
          _2 = context.domain.importFrom(cotos)
        )
      }

      case Msg.GeolocatedCotosFetched(Left(e)) =>
        default.copy(
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch geolocated cotos."))
        )
    }
  }

  def apply(
      geomap: Geomap
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    MapLibre(
      id = "main-geomap",
      center = geomap.center.toMapLibre,
      zoom = geomap.zoom,
      focusedLocation = geomap.focusedLocation.map(_.toMapLibre),
      markerDefs = context.domain.cotoMarkerDefs,
      applyCenterZoom = geomap._applyCenterZoom,
      addOrRemoveMarkers = geomap._addOrRemoveMarkers,
      refreshMarkers = geomap._refreshMarkers,
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
}
