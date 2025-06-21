package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/plugin-process", JSImport.Namespace)
object process extends js.Object {

  /** Exits immediately with the given exitCode.
    */
  def exit(code: Int): js.Promise[Unit] = js.native

  /** Exits the current instance of the app then relaunches it.
    */
  def relaunch(): js.Promise[Unit] = js.native
}
