package cotoami.models

import cotoami.libs.geomap.maplibre.LngLatBounds

case class GeoBounds(southwest: Geolocation, northeast: Geolocation) {
  val sw = southwest
  val ne = northeast

  def contains(location: Geolocation): Boolean = {
    val containsLatitude =
      this.sw.lat <= location.lat && location.lat <= this.ne.lat
    val containsLongitude =
      if (this.sw.lng <= this.ne.lng)
        this.sw.lng <= location.lng && location.lng <= this.ne.lng
      else // wrapped coordinates
        this.sw.lng >= location.lng && location.lng >= this.ne.lng

    containsLatitude && containsLongitude
  }

  def toMapLibre: LngLatBounds =
    new LngLatBounds(southwest.toMapLibre, northeast.toMapLibre)
}

object GeoBounds {
  def fromLngLat(sw: (Double, Double), ne: (Double, Double)): GeoBounds =
    GeoBounds(Geolocation.fromLngLat(sw), Geolocation.fromLngLat(ne))

  def fromMapLibre(bounds: LngLatBounds): GeoBounds =
    fromLngLat(bounds.getSouthWest().toArray(), bounds.getNorthEast().toArray())
}
