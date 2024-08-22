package cotoami.subparts

import slinky.core.facade.ReactElement

import cotoami.components.MapLibre
import cotoami.models.{Geolocation, Geomap}

object SectionGeomap {

  def apply(geomap: Geomap): ReactElement =
    MapLibre(
      id = "main-geomap",
      center = toLngLat(geomap.center),
      zoom = geomap.zoom,
      focusedLocation = geomap.focusedLocation.map(toLngLat),
      onClick = Some(e => {
        println(s"Clicked: ${e.lngLat}")
      })
    )

  private def toLngLat(location: Geolocation): (Double, Double) =
    (location.longitude, location.latitude)
}
