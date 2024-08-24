package cotoami.libs.geomap

import scala.scalajs.js
import scala.scalajs.js.|
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

  type LngLatLike = LngLat | js.Tuple2[Double, Double]

  @js.native
  @JSImport("maplibre-gl", "LngLatBounds")
  class LngLatBounds(sw: LngLatLike, ne: LngLatLike) extends js.Object {

    /** Check if the point is within the bounding box.
      */
    def contains(lnglat: LngLatLike): Boolean = js.native

    /** Extend the bounds to include a given LngLatLike or LngLatBoundsLike.
      */
    def extend(obj: LngLatLike | LngLatBoundsLike): LngLatBounds = js.native

    /** Returns the geographical coordinate equidistant from the bounding box's
      * corners.
      */
    def getCenter(): LngLat = js.native

    /** Returns the east edge of the bounding box.
      */
    def getEast(): Double = js.native

    /** Returns the north edge of the bounding box.
      */
    def getNorth(): Double = js.native

    /** Returns the northeast corner of the bounding box.
      */
    def getNorthEast(): LngLat = js.native

    /** Returns the northwest corner of the bounding box.
      */
    def getNorthWest(): LngLat = js.native

    /** Returns the south edge of the bounding box.
      */
    def getSouth(): Double = js.native

    /** Returns the southeast corner of the bounding box.
      */
    def getSouthEast(): LngLat = js.native

    /** Returns the southwest corner of the bounding box.
      */
    def getSouthWest(): LngLat = js.native

    /** Returns the west edge of the bounding box.
      */
    def getWest(): Double = js.native

    /** Check if the bounding box is an empty/null-type box.
      */
    def isEmpty(): Boolean = js.native

    /** Set the northeast corner of the bounding box.
      */
    def setNorthEast(ne: LngLatLike): LngLatBounds = js.native

    /** Set the southwest corner of the bounding box.
      */
    def setSouthWest(sw: LngLatLike): LngLatBounds = js.native

    /** Returns the bounding box represented as an array.
      */
    def toArray()
        : js.Tuple2[js.Tuple2[Double, Double], js.Tuple2[Double, Double]] =
      js.native
  }

  type LngLatBoundsLike =
    LngLatBounds |
      js.Tuple2[LngLatLike, LngLatLike] |
      js.Tuple4[Double, Double, Double, Double]
}
