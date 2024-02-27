package cotoami.tauri

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/api/dialog", JSImport.Namespace)
object Dialog extends js.Object {

  /** Open a file/directory selection dialog.
    *
    * The selected paths are added to the filesystem and asset protocol
    * allowlist scopes. When security is more important than the easy of use of
    * this API, prefer writing a dedicated command instead.
    *
    * Note that the allowlist scope change is not persisted, so the values are
    * cleared when the application is restarted. You can save it to the
    * filesystem using tauri-plugin-persisted-scope.
    *
    * <https://tauri.app/v1/api/js/dialog/#open>
    *
    * @param options
    * @return
    *   Promise<null | string | string[]>
    */
  def open(
      options: OpenDialogOptions
  ): js.Promise[scala.Null | String | js.Array[String]] = js.native

  trait DialogFilter extends js.Object {
    // Extensions to filter, without a . prefix.
    val extensions: js.Array[String]

    // Filter name.
    val name: String
  }

  trait OpenDialogOptions extends js.Object {
    // Initial directory or file path.
    val defaultPath: js.UndefOr[String] = js.undefined

    // Whether the dialog is a directory selection or not.
    val directory: js.UndefOr[Boolean] = js.undefined

    // Whether the dialog allows multiple selection or not.
    val multiple: js.UndefOr[Boolean] = js.undefined

    // If directory is true, indicates that it will be read recursively later.
    // Defines whether subdirectories will be allowed on the scope or not.
    val recursive: js.UndefOr[Boolean] = js.undefined

    // The title of the dialog window.
    val title: js.UndefOr[String] = js.undefined

    // The filters of the dialog.
    val filters: js.UndefOr[js.Array[DialogFilter]] = js.undefined
  }
}
