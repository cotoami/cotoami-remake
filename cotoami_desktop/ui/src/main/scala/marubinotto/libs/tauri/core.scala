package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/api/core", JSImport.Namespace)
object core extends js.Object {

  /** Convert a device file path to an URL that can be loaded by the webview.
    *
    * Note that asset: and http://asset.localhost must be added to
    * app.security.csp in tauri.conf.json.
    *
    * https://v2.tauri.app/reference/javascript/api/namespacecore/#convertfilesrc
    */
  def convertFileSrc(
      filePath: String,
      protocol: js.UndefOr[String] = js.undefined
  ): String =
    js.native

  /** Sends a message to the backend.
    *
    * https://v2.tauri.app/reference/javascript/api/namespacecore/#invoke
    *
    * @param cmd
    *   The command name.
    * @param args
    *   The optional arguments to pass to the command. It should be passed as a
    *   JSON object with camelCase keys (when declaring arguments in Rust using
    *   snake_case, the arguments are converted to camelCase for JavaScript).
    *   For the Rust side, it can be of any type, as long as they implement
    *   `serde::Deserialize`.
    * @return
    *   A promise resolving or rejecting to the backend response. For the Rust
    *   side, the returned data can be of any type, as long as it implements
    *   `serde::Serialize`.
    */
  def invoke[T](
      cmd: String,
      args: js.Object = js.Dynamic.literal()
  ): js.Promise[T] = js.native
}
