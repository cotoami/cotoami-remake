package cotoami.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/api/tauri", JSImport.Namespace)
object Tauri extends js.Object {

  /** Sends a message to the backend.
    *
    * @param cmd
    *   The command name.
    * @param args
    *   The optional arguments to pass to the command.
    * @return
    *   A promise resolving or rejecting to the backend response.
    */
  def invoke[T](
      cmd: String,
      args: js.Object = js.Dynamic.literal()
  ): js.Promise[T] = js.native
}
