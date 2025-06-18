package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

import marubinotto.libs.tauri.dpi._

@js.native
@JSImport("@tauri-apps/api/window", JSImport.Namespace)
object window extends js.Object {

  /** Get an instance of Window for the current window.
    */
  def getCurrentWindow(): Window = js.native

  @js.native
  class Window(label: String, options: WindowOptions) extends js.Object {

    /** Centers the window.
      */
    def center(): js.Promise[Unit] = js.native

    /** Clear any applied effects if possible.
      */
    def clearEffects(): js.Promise[Unit] = js.native

    /** Closes the webview.
      */
    def close(): js.Promise[Unit] = js.native

    /** Destroys the window. Behaves like Window.close but forces the window
      * close instead of emitting a closeRequested event.
      */
    def destroy(): js.Promise[Unit] = js.native

    /** Hide the webview.
      */
    def hide(): js.Promise[Unit] = js.native

    /** The physical size of the window's client area. The client area is the
      * content of the window, excluding the title bar and borders.
      */
    def innerSize(): js.Promise[PhysicalSize] = js.native

    /** The scale factor that can be used to map physical pixels to logical
      * pixels.
      */
    def scaleFactor(): js.Promise[Double] = js.native

    /** Resizes the window with a new inner size.
      */
    def setSize(size: PhysicalSize | LogicalSize): js.Promise[Unit] = js.native

    /** Sets the badge label macOS only.
      *
      * @param label
      *   The badge label. Use `undefined` to remove the badge.
      * @return
      *   A promise indicating the success or failure of the operation.
      */
    def setBadgeLabel(label: js.UndefOr[String]): js.Promise[Unit] = js.native
  }
}

trait WindowOptions extends js.Object {

  /** Whether clicking an inactive window also clicks through to the webview on
    * macOS.
    */
  val acceptFirstMouse: js.UndefOr[Boolean] = js.undefined
}
