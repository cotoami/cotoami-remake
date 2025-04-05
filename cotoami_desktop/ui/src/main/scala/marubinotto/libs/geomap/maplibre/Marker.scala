package marubinotto.libs.geomap.maplibre

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

@js.native
@JSImport("maplibre-gl", "Marker")
class Marker(options: js.UndefOr[MarkerOptions] = js.undefined)
    extends js.Object {

  /** Attaches the Marker to a Map object.
    */
  def addTo(map: Map): Marker = js.native

  /** Set the marker's geographical position and move it.
    */
  def setLngLat(lnglat: LngLatLike): Marker = js.native

  /** Removes the marker from a map.
    */
  def remove(): Marker = js.native

  /** Adds a CSS class to the marker element.
    */
  def addClassName(className: String): Unit = js.native

  /** Removes a CSS class from the marker element.
    */
  def removeClassName(className: String): Unit = js.native

  /** Returns the Marker's HTML element.
    */
  def getElement(): dom.HTMLElement = js.native

  /** Adds a listener to a specified event type.
    */
  def on[Event](
      eventType: String,
      listener: js.Function1[Event, Unit]
  ): Map = js.native

}

trait MarkerOptions extends js.Object {

  /** Space-separated CSS class names to add to marker element.
    */
  val className: js.UndefOr[String] = js.undefined

  /** The color to use for the default marker if options.element is not
    * provided. The default is light blue.
    */
  val color: js.UndefOr[String] = js.undefined

  /** DOM element to use as a marker. The default is a light blue,
    * droplet-shaped SVG marker.
    */
  val element: js.UndefOr[dom.Element] = js.undefined
}
