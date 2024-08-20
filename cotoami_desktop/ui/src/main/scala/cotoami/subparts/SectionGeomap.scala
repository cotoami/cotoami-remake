package cotoami.subparts

import slinky.core.facade.ReactElement

import cotoami.components.MapLibre
import cotoami.models.Geolocation

object SectionGeomap {

  case class Model(
      position: Geolocation = Geolocation.default,
      zoom: Int = 8
  )

  def apply(model: Model): ReactElement =
    MapLibre(
      id = "main-geomap",
      position = toLngLat(model.position),
      zoom = model.zoom
    )

  private def toLngLat(location: Geolocation): (Double, Double) =
    (location.longitude, location.latitude)
}
