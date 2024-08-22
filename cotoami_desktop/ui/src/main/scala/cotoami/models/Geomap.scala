package cotoami.models

case class Geomap(
    center: Geolocation = Geolocation.default,
    zoom: Int = 8,
    focusedLocation: Option[Geolocation] = None,
    forceSync: Int = 0
)

case class Geolocation(longitude: Double, latitude: Double)

object Geolocation {
  // The Tokyo station
  val default: Geolocation = Geolocation(139.76730676352, 35.680959106959)

  def fromLngLat(lngLat: (Double, Double)): Geolocation =
    Geolocation(lngLat._1, lngLat._2)
}
