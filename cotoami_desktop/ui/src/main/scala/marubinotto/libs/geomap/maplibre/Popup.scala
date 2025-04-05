package marubinotto.libs.geomap.maplibre

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("maplibre-gl", "Popup")
class Popup(options: js.UndefOr[PopupOptions] = js.undefined)
    extends js.Object {

  /** Adds the popup to a map.
    */
  def addTo(map: Map): Popup = js.native

  /** Sets the geographical location of the popup's anchor, and moves the popup
    * to it. Replaces trackPointer() behavior.
    */
  def setLngLat(lnglat: LngLatLike): Popup = js.native

  /** Sets the popup's content to the HTML provided as a string.
    *
    * This method does not perform HTML filtering or sanitization, and must be
    * used only with trusted content. Consider Popup#setText if the content is
    * an untrusted text string.
    */
  def setHTML(html: String): Popup = js.native

  /** Sets the popup's content to a string of text.
    *
    * This function creates a Text node in the DOM, so it cannot insert raw
    * HTML. Use this method for security against XSS if the popup content is
    * user-provided.
    */
  def setText(text: String): Popup = js.native

  /** Removes the popup from the map it has been added to.
    */
  def remove(): Popup = js.native
}

trait PopupOptions extends js.Object {

  /** Space-separated CSS class names to add to popup container
    */
  val className: js.UndefOr[String] = js.undefined

  /** If true, a close button will appear in the top right corner of the popup.
    *
    * Default Value: true
    */
  val closeButton: js.UndefOr[Boolean] = js.undefined

  /** If true, the popup will closed when the map is clicked.
    *
    * Default Value: true
    */
  val closeOnClick: js.UndefOr[Boolean] = js.undefined

  /** If true, the popup will closed when the map moves.
    *
    * Default Value: false
    */
  val closeOnMove: js.UndefOr[Boolean] = js.undefined

  /** If true, the popup will try to focus the first focusable element inside
    * the popup.
    *
    * Default Value: true
    */
  val focusAfterOpen: js.UndefOr[Boolean] = js.undefined

  /** A string that sets the CSS property of the popup's maximum width, eg
    * '300px'. To ensure the popup resizes to fit its content, set this property
    * to 'none'. Available values can be found here:
    * https://developer.mozilla.org/en-US/docs/Web/CSS/max-width
    *
    * Default Value: "240px"
    */
  val maxWidth: js.UndefOr[String] = js.undefined

  /** A pixel offset applied to the popup's location
    */
  val offset: js.UndefOr[Double] = js.undefined

  /** If true, rounding is disabled for placement of the popup, allowing for
    * subpixel positioning and smoother movement when the popup is translated.
    *
    * Default Value: false
    */
  val subpixelPositioning: js.UndefOr[Boolean] = js.undefined
}
