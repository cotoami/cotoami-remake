package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/plugin-updater", JSImport.Namespace)
object updater extends js.Object {

  @js.native
  class Update(metadata: js.Object) extends js.Object {
    val version: String = js.native
    val currentVersion: String = js.native
    val date: js.UndefOr[String] = js.native
    val body: js.UndefOr[String] = js.native
  }
}
