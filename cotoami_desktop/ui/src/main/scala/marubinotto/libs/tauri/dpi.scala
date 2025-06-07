package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/api/dpi", JSImport.Namespace)
object dpi extends js.Object {

  /** A size represented in logical pixels. Logical pixels are scaled according
    * to the window’s DPI scale. Most browser APIs (i.e. MouseEvent’s clientX)
    * will return logical pixels.
    */
  @js.native
  class LogicalSize(val width: Double, val height: Double) extends js.Object {
    val `type`: String = js.native
  }

  /** A size represented in physical pixels.
    *
    * Physical pixels represent actual screen pixels, and are DPI-independent.
    * For high-DPI windows, this means that any point in the window on the
    * screen will have a different position in logical pixels.
    */
  @js.native
  class PhysicalSize(val width: Double, val height: Double) extends js.Object {
    val `type`: String = js.native

    /** Converts the physical size to a logical one.
      */
    def toLogical(scaleFactor: Double): LogicalSize = js.native
  }
}
