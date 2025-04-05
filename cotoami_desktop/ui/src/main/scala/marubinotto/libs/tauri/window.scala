package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/api/window", JSImport.Namespace)
object window extends js.Object {

  val appWindow: WebviewWindow = js.native

  @js.native
  class WebviewWindow(label: String, options: WindowOptions) extends js.Object {

    /** Centers the window.
      */
    def center(): js.Promise[Unit] = js.native

    /** Closes the window.
      */
    def close(): js.Promise[Unit] = js.native

    /** The scale factor that can be used to map physical pixels to logical
      * pixels.
      */
    def scaleFactor(): js.Promise[Double] = js.native

    /** The physical size of the window's client area. The client area is the
      * content of the window, excluding the title bar and borders.
      */
    def innerSize(): js.Promise[PhysicalSize] = js.native

    /** The physical size of the entire window. These dimensions include the
      * title bar and borders. If you don't want that (and you usually don't),
      * use `innerSize` instead.
      */
    def outerSize(): js.Promise[PhysicalSize] = js.native

    /** Resizes the window with a new inner size.
      */
    def setSize(size: PhysicalSize | LogicalSize): js.Promise[Unit] = js.native
  }

  @js.native
  class PhysicalSize(val width: Double, val height: Double) extends js.Object {
    val `type`: String = js.native

    def toLogical(scaleFactor: Double): LogicalSize = js.native
  }

  @js.native
  class LogicalSize(val width: Double, val height: Double) extends js.Object {
    val `type`: String = js.native
  }
}

trait WindowOptions extends js.Object {

  /** Whether clicking an inactive window also clicks through to the webview on
    * macOS.
    */
  val acceptFirstMouse: js.UndefOr[Boolean] = js.undefined
}
