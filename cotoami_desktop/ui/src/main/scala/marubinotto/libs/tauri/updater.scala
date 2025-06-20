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

  /** Options used when downloading an update
    */
  trait DownloadOptions extends js.Object {

    /** Timeout in milliseconds
      */
    val timeout: js.UndefOr[Double] = js.undefined
  }

  @js.native
  trait DownloadEvent extends js.Object {

    /** Event type: 'Started', 'Progress', 'Finished'
      */
    val event: String = js.native

    val data: js.UndefOr[DownloadEventData] = js.native
  }

  @js.native
  trait DownloadEventData extends js.Object {
    // When 'Started'
    val contentLength: js.UndefOr[Double] = js.native

    // When 'Progress'
    val chunkLength: js.UndefOr[Double] = js.native
  }
}
