package cotoami.subparts

import slinky.core.facade.ReactElement

import cotoami.libs.geomap.maplibre
import cotoami.components.MapLibre
import cotoami.models.{Geolocation, Geomap}

object SectionGeomap {

  val onClick: Option[maplibre.MapMouseEvent => Unit] = Some(e => {
    println(s"Clicked: ${e.lngLat}")
  })

  def apply(geomap: Geomap): ReactElement =
    MapLibre(
      id = "main-geomap",
      center = toLngLat(geomap.center),
      zoom = geomap.zoom,
      focusedLocation = geomap.focusedLocation.map(toLngLat),
      onClick = SectionGeomap.onClick
    )

  private def toLngLat(location: Geolocation): (Double, Double) =
    (location.longitude, location.latitude)
}
