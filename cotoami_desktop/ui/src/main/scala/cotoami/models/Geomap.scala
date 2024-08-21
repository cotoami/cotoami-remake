package cotoami.models

case class Geomap(
    center: Geolocation = Geolocation.default,
    zoom: Int = 8,
    focusedLocation: Option[Geolocation] = None
)

case class Geolocation(longitude: Double, latitude: Double)

object Geolocation {
  // The Tokyo station
  val default: Geolocation = Geolocation(139.76730676352, 35.680959106959)
}
