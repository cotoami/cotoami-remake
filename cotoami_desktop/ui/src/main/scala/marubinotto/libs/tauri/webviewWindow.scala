package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

import marubinotto.libs.tauri.dpi._

@js.native
@JSImport("@tauri-apps/api/webviewWindow", JSImport.Namespace)
object webviewWindow extends js.Object {

  /** Get an instance of Webview for the current webview window.
    */
  def getCurrentWebviewWindow(): WebviewWindow = js.native

  /** Create new webview or get a handle to an existing one.
    *
    * Webviews are identified by a label a unique identifier that can be used to
    * reference it later. It may only contain alphanumeric characters a-zA-Z
    * plus the following special characters -, /, : and _.
    */
  @js.native
  class WebviewWindow(label: String, options: WindowOptions) extends js.Object {

    /** Centers the window.
      */
    def center(): js.Promise[Unit] = js.native

    /** Clears all browsing data for this webview.
      */
    def clearAllBrowsingData(): js.Promise[Unit] = js.native

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
  }
}

trait WindowOptions extends js.Object {

  /** Whether clicking an inactive window also clicks through to the webview on
    * macOS.
    */
  val acceptFirstMouse: js.UndefOr[Boolean] = js.undefined
}
