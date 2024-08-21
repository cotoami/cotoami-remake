package cotoami.libs.geomap

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

package object maplibre {

  /** Adds a custom load resource function that will be called when using a URL
    * that starts with a custom url schema. This will happen in the main thread,
    * and workers might call it if they don't know how to handle the protocol.
    */
  @js.native
  @JSImport("maplibre-gl", "addProtocol")
  def addProtocol(customProtocol: String, loadFn: js.Any): Unit = js.native

  /** A LngLat object represents a given longitude and latitude coordinate,
    * measured in degrees. These coordinates are based on the WGS84 (EPSG:4326)
    * standard.
    */
  @js.native
  @JSImport("maplibre-gl", "LngLat")
  class LngLat(lng: Double, lat: Double) extends js.Object {

    /** Returns the approximate distance between a pair of coordinates in meters
      * Uses the Haversine Formula (from R.W. Sinnott, "Virtues of the
      * Haversine", Sky and Telescope, vol. 68, no. 2, 1984, p. 159)
      *
      * @param lngLat
      *   coordinates to compute the distance to
      * @return
      *   Distance in meters between the two coordinates.
      */
    def distanceTo(lngLat: LngLat): Double = js.native

    /** Returns the coordinates represented as an array of two numbers.
      */
    def toArray(): js.Tuple2[Double, Double] = js.native

    /** Returns a new LngLat object whose longitude is wrapped to the range
      * (-180, 180).
      */
    def wrap(): LngLat = js.native
  }
}
