package cotoami.models

import cotoami.libs.geomap.maplibre.{LngLat, LngLatBounds}

case class Geomap(
    center: Geolocation = Geolocation.default,
    zoom: Double = 8,
    syncCenterZoom: Int = 0,
    focusedLocation: Option[Geolocation] = None
)

case class Geolocation(longitude: Double, latitude: Double)

object Geolocation {
  // The Tokyo station
  val default: Geolocation = Geolocation(139.76730676352, 35.680959106959)

  def fromLngLat(lngLat: (Double, Double)): Geolocation =
    Geolocation(lngLat._1, lngLat._2)

  def fromMapLibre(lngLat: LngLat): Geolocation = fromLngLat(lngLat.toArray())
}

case class GeoBounds(southwest: Geolocation, northeast: Geolocation)

object GeoBounds {
  def fromLngLat(sw: (Double, Double), ne: (Double, Double)): GeoBounds =
    GeoBounds(Geolocation.fromLngLat(sw), Geolocation.fromLngLat(ne))

  def fromMapLibre(bounds: LngLatBounds): GeoBounds =
    fromLngLat(bounds.getSouthWest().toArray(), bounds.getNorthEast().toArray())
}
