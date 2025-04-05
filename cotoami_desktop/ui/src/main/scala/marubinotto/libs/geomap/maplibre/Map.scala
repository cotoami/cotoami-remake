package marubinotto.libs.geomap.maplibre

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

  /** Clean up and release all internal resources associated with this map.
    *
    * This includes DOM elements, event bindings, web workers, and WebGL
    * resources.
    *
    * Use this method when you are done using the map and wish to ensure that it
    * no longer consumes browser resources. Afterwards, you must not call any
    * other methods on the map.
    */
  def remove(): Unit = js.native

  /** Returns the map's geographical centerpoint.
    */
  def getCenter(): LngLat = js.native

  /** Returns the map's current zoom level.
    */
  def getZoom(): Double = js.native

  /** Returns the map's geographical bounds. When the bearing or pitch is
    * non-zero, the visible region is not an axis-aligned rectangle, and the
    * result is the smallest bounds that encompasses the visible region.
    */
  def getBounds(): LngLatBounds = js.native

  /** Changes any combination of center, zoom, bearing, pitch, and padding with
    * an animated transition between old and new values. The map will retain its
    * current values for any details not specified in options.
    *
    * Note: The transition will happen instantly if the user has enabled the
    * reduced motion accessibility feature enabled in their operating system,
    * unless options includes essential: true.
    */
  def easeTo(options: EaseToOptions): Map = js.native

  /** Changes any combination of center, zoom, bearing, and pitch, animating the
    * transition along a curve that evokes flight. The animation seamlessly
    * incorporates zooming and panning to help the user maintain her bearings
    * even after traversing a great distance.
    *
    * Note: The animation will be skipped, and this will behave equivalently to
    * jumpTo if the user has the reduced motion accessibility feature enabled in
    * their operating system, unless 'options' includes essential: true.
    */
  def flyTo(options: FlyToOptions): Map = js.native

  /** Pans and zooms the map to contain its visible area within the specified
    * geographical bounds. This function will also reset the map's bearing to 0
    * if bearing is nonzero.
    */
  def fitBounds(
      bounds: LngLatBoundsLike,
      options: js.UndefOr[FitBoundsOptions] = js.undefined
  ): Map = js.native

  /** Adds a listener for events of a specified type, optionally limited to
    * features in a specified style layer.
    *
    * @param type
    *   The event type to listen for.
    * @param layer
    *   The ID of a style layer. Event will only be triggered if its location is
    *   within a visible feature in this layer.
    * @param listener
    *   The function to be called when the event is fired.
    * @return
    */
  def on[Event](
      eventType: String,
      layer: String,
      listener: js.Function1[Event, Unit]
  ): Map = js.native

  def on[Event](
      eventType: String,
      listener: js.Function1[Event, Unit]
  ): Map = js.native

  /** Removes an event listener for events previously added with Map#on.
    */
  def off[Event](
      eventType: String,
      layer: String,
      listener: js.Function1[Event, Unit]
  ): Map = js.native

  def off[Event](
      eventType: String,
      listener: js.Function1[Event, Unit]
  ): Map = js.native

  /** The map's DragRotateHandler, which implements rotating the map while
    * dragging with the right mouse button or with the Control key pressed.
    */
  val dragRotate: DragRotateHandler = js.native

  /** The map's KeyboardHandler, which allows the user to zoom, rotate, and pan
    * the map using keyboard shortcuts.
    */
  val keyboard: KeyboardHandler = js.native

  /** The map's TwoFingersTouchZoomRotateHandler, which allows the user to zoom
    * or rotate the map with touch gestures.
    */
  val touchZoomRotate: TwoFingersTouchZoomRotateHandler = js.native
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
  val zoom: js.UndefOr[Double] = js.undefined

  /** The initial geographical centerpoint of the map.
    *
    * If center is not specified in the constructor options, MapLibre GL JS will
    * look for it in the map's style object. If it is not specified in the
    * style, either, it will default to [0, 0]
    */
  val center: js.UndefOr[LngLatLike] = js.undefined

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

trait CenterZoomBearing extends js.Object {

  /** The desired center.
    */
  val center: js.UndefOr[LngLatLike] = js.undefined

  /** The desired zoom level.
    */
  val zoom: js.UndefOr[Double] = js.undefined

  /** The desired bearing in degrees. The bearing is the compass direction that
    * is "up". For example, bearing: 90 orients the map so that east is up.
    */
  val bearing: js.UndefOr[Double] = js.undefined
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

/** Options common to Map#jumpTo, Map#easeTo, and Map#flyTo, controlling the
  * desired location, zoom, bearing, and pitch of the camera.
  */
trait CameraOptions extends CenterZoomBearing {

  /** If zoom is specified, around determines the point around which the zoom is
    * centered.
    */
  val around: js.UndefOr[LngLatLike] = js.undefined

  /** The desired pitch in degrees. The pitch is the angle towards the horizon
    * measured in degrees with a range between 0 and 60 degrees. For example,
    * pitch: 0 provides the appearance of looking straight down at the map,
    * while pitch: 60 tilts the user's perspective towards the horizon.
    * Increasing the pitch value is often used to display 3D objects.
    */
  val pitch: js.UndefOr[Double] = js.undefined
}

trait EaseToOptions extends CameraOptions with AnimationOptions

trait FlyToOptions extends CameraOptions with AnimationOptions {

  /** The zooming "curve" that will occur along the flight path. A high value
    * maximizes zooming for an exaggerated animation, while a low value
    * minimizes zooming for an effect closer to Map#easeTo. 1.42 is the average
    * value selected by participants in the user study discussed in van Wijk
    * (2003). A value of Math.pow(6, 0.25) would be equivalent to the root mean
    * squared average velocity. A value of 1 would produce a circular motion.
    *
    * Default Value: 1.42
    */
  val curve: js.UndefOr[Double] = js.undefined

  /** The animation's maximum duration, measured in milliseconds. If duration
    * exceeds maximum duration, it resets to 0.
    */
  val maxDuration: js.UndefOr[Int] = js.undefined

  /** The zero-based zoom level at the peak of the flight path. If options.curve
    * is specified, this option is ignored.
    */
  val minZoom: js.UndefOr[Double] = js.undefined

  /** The amount of padding in pixels to add to the given bounds.
    */
  val padding: js.UndefOr[Double] = js.undefined

  /** The average speed of the animation measured in screenfuls per second,
    * assuming a linear timing curve. If options.speed is specified, this option
    * is ignored.
    */
  val screenSpeed: js.UndefOr[Double] = js.undefined

  /** The average speed of the animation defined in relation to options.curve. A
    * speed of 1.2 means that the map appears to move along the flight path by
    * 1.2 times options.curve screenfuls every second. A screenful is the map's
    * visible span. It does not correspond to a fixed physical distance, but
    * varies by zoom level.
    *
    * Default Value: 1.2
    */
  val speed: js.UndefOr[Double] = js.undefined
}

trait FitBoundsOptions extends FlyToOptions {

  /** If true, the map transitions using Map#easeTo. If false, the map
    * transitions using Map#flyTo.
    *
    * Default Value: false
    */
  val linear: js.UndefOr[Boolean] = js.undefined

  /** The maximum zoom level to allow when the map view transitions to the
    * specified bounds.
    */
  val maxZoom: js.UndefOr[Double] = js.undefined

  /** The center of the given bounds relative to the map's center, measured in
    * pixels.
    */
  val offset: js.UndefOr[js.Tuple2[Int, Int]] = js.undefined
}

@js.native
trait MapMouseEvent extends js.Object {

  /** Prevents subsequent default processing of the event by the map.
    */
  def preventDefault(): Unit = js.native

  /** true if preventDefault has been called.
    */
  def defaultPrevented(): Boolean = js.native

  /** The geographic location on the map of the mouse cursor.
    */
  val lngLat: LngLat = js.native

  /** The event type.
    */
  val `type`: String = js.native
}

@js.native
trait MapLibreEvent extends js.Object {
  val target: Map = js.native
}

@js.native
trait DragRotateHandler extends js.Object {

  /** Disables the "drag to rotate" interaction.
    */
  def disable(): Unit = js.native

  /** Enables the "drag to rotate" interaction.
    */
  def enable(): Unit = js.native

  /** Returns a Boolean indicating whether the "drag to rotate" interaction is
    * active.
    */
  def isActive(): Boolean = js.native

  /** Returns a Boolean indicating whether the "drag to rotate" interaction is
    * enabled.
    */
  def isEnabled(): Boolean = js.native
}

@js.native
trait KeyboardHandler extends js.Object {

  /** Disables the "keyboard rotate and zoom" interaction.
    */
  def disable(): Unit = js.native

  /** Disables the "keyboard pan/rotate" interaction, leaving the "keyboard
    * zoom" interaction enabled.
    */
  def disableRotation(): Unit = js.native

  /** Enables the "keyboard rotate and zoom" interaction.
    */
  def enable(): Unit = js.native

  /** Enables the "keyboard pan/rotate" interaction.
    */
  def enableRotation(): Unit = js.native

  /** Returns true if the handler is enabled and has detected the start of a
    * zoom/rotate gesture.
    */
  def isActive(): Boolean = js.native

  /** Returns a Boolean indicating whether the "keyboard rotate and zoom"
    * interaction is enabled.
    */
  def isEnabled(): Boolean = js.native

  /** reset can be called by the manager at any time and must reset everything
    * to it's original state
    */
  def reset(): Unit = js.native
}

@js.native
trait TwoFingersTouchZoomRotateHandler extends js.Object {

  /** Disables the "pinch to rotate and zoom" interaction.
    */
  def disable(): Unit = js.native

  /** Disables the "pinch to rotate" interaction, leaving the "pinch to zoom"
    * interaction enabled.
    */
  def disableRotation(): Unit = js.native

  /** Enables the "pinch to rotate and zoom" interaction.
    */
  def enable(): Unit = js.native

  /** Enables the "pinch to rotate" interaction.
    */
  def enableRotation(): Unit = js.native

  /** Returns true if the handler is enabled and has detected the start of a
    * zoom/rotate gesture.
    */
  def isActive(): Boolean = js.native

  /** Returns a Boolean indicating whether the "pinch to rotate and zoom"
    * interaction is enabled.
    */
  def isEnabled(): Boolean = js.native
}
