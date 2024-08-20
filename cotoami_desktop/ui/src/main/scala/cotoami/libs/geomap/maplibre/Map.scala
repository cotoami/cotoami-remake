package cotoami.libs.geomap.maplibre

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

@js.native
@JSImport("maplibre-gl", "Map")
class Map(options: MapOptions) extends js.Object {

  /** Adds an IControl to the map, calling control.onAdd(this).
    *
    * @param position
    *   Valid values are 'top-left', 'top-right', 'bottom-left', and
    *   'bottom-right'. Defaults to 'top-right'.
    */
  def addControl(
      control: js.Any,
      position: js.UndefOr[String] = js.undefined
  ): Map = js.native

  /** Changes any combination of center, zoom, bearing, pitch, and padding with
    * an animated transition between old and new values. The map will retain its
    * current values for any details not specified in options.
    *
    * Note: The transition will happen instantly if the user has enabled the
    * reduced motion accessibility feature enabled in their operating system,
    * unless options includes essential: true.
    */
  def easeTo(options: EaseToOptions): Map = js.native
}

trait MapOptions extends js.Object {

  /** The HTML element in which MapLibre GL JS will render the map, or the
    * element's string id. The specified element must have no children.
    */
  val container: js.UndefOr[dom.HTMLElement | String] = js.undefined

  /** The map's MapLibre style.
    *
    * This must be a JSON object conforming to the schema described in the
    * MapLibre Style Specification, or a URL to such JSON. When the style is not
    * specified, calling Map#setStyle is required to render the map.
    */
  val style: js.UndefOr[String] = js.undefined

  /** The initial zoom level of the map.
    *
    * If zoom is not specified in the constructor options, MapLibre GL JS will
    * look for it in the map's style object. If it is not specified in the
    * style, either, it will default to 0.
    */
  val zoom: js.UndefOr[Int] = js.undefined

  /** The initial geographical centerpoint of the map.
    *
    * If center is not specified in the constructor options, MapLibre GL JS will
    * look for it in the map's style object. If it is not specified in the
    * style, either, it will default to [0, 0]
    */
  val center: js.UndefOr[js.Tuple2[Double, Double]] = js.undefined

  /** A callback run before the Map makes a request for an external URL.
    *
    * The callback can be used to modify the url, set headers, or set the
    * credentials property for cross-origin requests. Expected to return an
    * object with a url property and optionally headers and credentials
    * properties.
    */
  val transformRequest
      : js.UndefOr[js.Function2[String, String, RequestParameters]] =
    js.undefined
}

trait RequestParameters extends js.Object {
  val url: String
}

/** Options common to map movement methods that involve animation, such as
  * Map#panBy and Map#easeTo, controlling the duration and easing function of
  * the animation.
  */
trait AnimationOptions extends js.Object {

  /** If false, no animation will occur.
    */
  val animate: js.UndefOr[Boolean] = js.undefined

  /** The animation's duration, measured in milliseconds.
    */
  val duration: js.UndefOr[Int] = js.undefined

  /** A function taking a time in the range 0..1 and returning a number where 0
    * is the initial state and 1 is the final state.
    */
  val easing: js.UndefOr[js.Function1[Double, Double]] = js.undefined

  /** If true, then the animation is considered essential and will not be
    * affected by prefers-reduced-motion.
    */
  val essential: js.UndefOr[Boolean] = js.undefined
}

trait CenterZoomBearing extends js.Object {

  /** The desired center.
    */
  val center: js.UndefOr[js.Tuple2[Double, Double]] = js.undefined

  /** The desired zoom level.
    */
  val zoom: js.UndefOr[Int] = js.undefined
}

trait EaseToOptions extends AnimationOptions with CenterZoomBearing

/** A NavigationControl control contains zoom buttons and a compass.
  */
@js.native
@JSImport("maplibre-gl", "NavigationControl")
class NavigationControl extends js.Object
