package cotoami.models

sealed trait CotonomaLocation

object CotonomaLocation {
  case class Center(location: Geolocation) extends CotonomaLocation
  case class Bounds(bounds: GeoBounds) extends CotonomaLocation
}
