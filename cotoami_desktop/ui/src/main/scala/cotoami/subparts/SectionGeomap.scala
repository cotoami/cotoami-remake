package cotoami.subparts

import slinky.core.facade.ReactElement

import cotoami.components.MapLibre
import cotoami.models.Geolocation

object SectionGeomap {

  case class Model(
      center: Geolocation = Geolocation.default,
      zoom: Int = 8,
      focusedLocation: Option[Geolocation] = None
  )

  def apply(model: Model): ReactElement =
    MapLibre(
      id = "main-geomap",
      center = toLngLat(model.center),
      zoom = model.zoom,
      focusedLocation = model.focusedLocation.map(toLngLat)
    )

  private def toLngLat(location: Geolocation): (Double, Double) =
    (location.longitude, location.latitude)
}
