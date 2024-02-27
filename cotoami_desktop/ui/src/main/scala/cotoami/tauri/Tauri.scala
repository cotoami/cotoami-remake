package cotoami.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/api/tauri", JSImport.Namespace)
object Tauri extends js.Object {

  /** Sends a message to the backend.
    *
    * <https://tauri.app/v1/api/js/tauri#invoke>
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
    *   A promise resolving or rejecting to the backend response.
    */
  def invoke[T](
      cmd: String,
      args: js.Object = js.Dynamic.literal()
  ): js.Promise[T] = js.native
}
