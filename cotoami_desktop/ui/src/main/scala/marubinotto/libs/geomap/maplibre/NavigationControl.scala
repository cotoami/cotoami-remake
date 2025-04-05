package marubinotto.libs.geomap.maplibre

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** A NavigationControl control contains zoom buttons and a compass.
  */
@js.native
@JSImport("maplibre-gl", "NavigationControl")
class NavigationControl(
    options: js.UndefOr[NavigationControlOptions] = js.undefined
) extends js.Object

trait NavigationControlOptions extends js.Object {

  /** If true the compass button is included.
    */
  val showCompass: js.UndefOr[Boolean] = js.undefined

  /** If true the zoom-in and zoom-out buttons are included.
    */
  val showZoom: js.UndefOr[Boolean] = js.undefined

  /** If true the pitch is visualized by rotating X-axis of compass.
    */
  val visualizePitch: js.UndefOr[Boolean] = js.undefined
}
