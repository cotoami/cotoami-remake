package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import marubinotto.facade.Nullable

@js.native
@JSImport("@tauri-apps/plugin-updater", JSImport.Namespace)
object updater extends js.Object {

  /** Check for updates, resolves to null if no updates are available.
    */
  def check(options: js.UndefOr[CheckOptions]): js.Promise[Nullable[Update]] =
    js.native

  /** Options used when checking for updates
    */
  trait CheckOptions extends js.Object {

    /** Allow downgrades to previous versions by not checking if the current
      * version is greater than the available version.
      */
    val allowDowngrades: js.UndefOr[Boolean] = js.undefined

    /** A proxy url to be used when checking and downloading updates.
      */
    val proxy: js.UndefOr[String] = js.undefined

    /** Target identifier for the running application. This is sent to the
      * backend.
      */
    val target: js.UndefOr[String] = js.undefined

    /** Timeout in milliseconds
      */
    val timeout: js.UndefOr[Double] = js.undefined
  }

  @js.native
  class Update(metadata: js.Object) extends js.Object {
    val version: String = js.native
    val currentVersion: String = js.native
    val date: js.UndefOr[String] = js.native
    val body: js.UndefOr[String] = js.native

    /** Destroys and cleans up this resource from memory. You should not call
      * any method on this object anymore and should drop any reference to it.
      */
    def close(): js.Promise[Unit] = js.native

    /** Download the updater package.
      */
    def download(
        onEvent: js.Function1[DownloadEvent, Unit],
        options: js.UndefOr[DownloadOptions]
    ): js.Promise[Unit] = js.native

    /** Install downloaded updater package.
      */
    def install(): js.Promise[Unit] = js.native

    /** Downloads the updater package and installs it.
      */
    def downloadAndInstall(
        onEvent: js.Function1[DownloadEvent, Unit],
        options: js.UndefOr[DownloadOptions]
    ): js.Promise[Unit] = js.native
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
