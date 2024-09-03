package cotoami.subparts

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{log_info, Context, Msg => AppMsg}
import cotoami.backend.{ErrorJson, GeolocatedCotos}
import cotoami.components.MapLibre
import cotoami.repositories.Domain
import cotoami.models.{GeoBounds, Geolocation}

object SectionGeomap {

  case class Model(
      center: Geolocation = Geolocation.default,
      zoom: Double = 8,
      focusedLocation: Option[Geolocation] = None,
      bounds: Option[GeoBounds] = None,
      _applyCenterZoom: Int = 0,
      _addOrRemoveMarkers: Int = 0,
      _refreshMarkers: Int = 0,
      _fitBounds: Int = 0
  ) {
    def moveTo(location: Geolocation): Model =
      this.copy(
        center = location,
        zoom = 13,
        _applyCenterZoom = this._applyCenterZoom + 1
      )

    def focus(location: Geolocation): Model =
      this.moveTo(location).copy(
        focusedLocation = Some(location)
      )

    def addOrRemoveMarkers: Model =
      this.copy(_addOrRemoveMarkers = this._addOrRemoveMarkers + 1)

    def refreshMarkers: Model =
      this.copy(_refreshMarkers = this._refreshMarkers + 1)

    def fitBounds(bounds: GeoBounds): Model =
      this.copy(bounds = Some(bounds), _fitBounds = this._fitBounds + 1)
  }

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.SectionGeomapMsg(this)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): (T => AppMsg) =
      tagger andThen AppMsg.SectionGeomapMsg

    case class Init(bounds: GeoBounds) extends Msg
    case class LocationClicked(location: Geolocation) extends Msg
    case class ZoomChanged(zoom: Double) extends Msg
    case class CenterMoved(center: Geolocation) extends Msg
    case class BoundsChanged(bounds: GeoBounds) extends Msg
    case class GeolocatedCotosFetched(
        result: Either[ErrorJson, GeolocatedCotos]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Domain, Seq[Cmd[AppMsg]]) = {
    val default = (model, context.domain, Seq.empty)
    msg match {
      case Msg.Init(bounds) => default.copy(_1 = model.addOrRemoveMarkers)

      case Msg.LocationClicked(location) =>
        default.copy(_1 = model.copy(focusedLocation = Some(location)))

      case Msg.ZoomChanged(zoom) =>
        default.copy(_1 = model.copy(zoom = zoom))

      case Msg.CenterMoved(center) =>
        default.copy(_1 = model.copy(center = center))

      case Msg.BoundsChanged(bounds) => {
        println(s"bounds changed: ${bounds}")
        default
      }

      case Msg.GeolocatedCotosFetched(Right(cotos)) => {
        val center = context.domain.currentCotonomaCoto.flatMap(_.geolocation)
        default.copy(
          _1 = (cotos.geoBounds match {
            case Some(Right(bounds)) =>
              center.map(model.moveTo(_)).getOrElse(model.fitBounds(bounds))
            case Some(Left(location)) =>
              model.moveTo(center.getOrElse(location))
            case None => center.map(model.moveTo(_)).getOrElse(model)
          }).addOrRemoveMarkers,
          _2 = context.domain.importFrom(cotos),
          _3 = Seq(
            log_info(s"Geolocated cotos fetched.", Some(cotos.debug))
          )
        )
      }

      case Msg.GeolocatedCotosFetched(Left(e)) =>
        default.copy(
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch geolocated cotos."))
        )
    }
  }

  def apply(
      model: Model
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    MapLibre(
      id = "main-geomap",
      center = model.center.toMapLibre,
      zoom = model.zoom,
      focusedLocation = model.focusedLocation.map(_.toMapLibre),
      markerDefs = context.domain.cotoMarkerDefs,
      bounds = model.bounds.map(_.toMapLibre),
      applyCenterZoom = model._applyCenterZoom,
      addOrRemoveMarkers = model._addOrRemoveMarkers,
      refreshMarkers = model._refreshMarkers,
      fitBounds = model._fitBounds,
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
