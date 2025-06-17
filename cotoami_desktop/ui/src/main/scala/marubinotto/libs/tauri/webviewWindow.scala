package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

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
  class WebviewWindow(label: String, options: WindowOptions)
      extends window.Window(label, options) {

    /** Clears all browsing data for this webview.
      */
    def clearAllBrowsingData(): js.Promise[Unit] = js.native
  }
}
