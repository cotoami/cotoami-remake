package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/plugin-dialog", JSImport.Namespace)
object dialog extends js.Object {

  /** Open a file/directory selection dialog.
    *
    * The selected paths are added to the filesystem and asset protocol scopes.
    * When security is more important than the easy of use of this API, prefer
    * writing a dedicated command instead.
    *
    * Note that the scope change is not persisted, so the values are cleared
    * when the application is restarted. You can save it to the filesystem using
    * tauri-plugin-persisted-scope.
    *
    * https://v2.tauri.app/reference/javascript/dialog/#open
    *
    * @param options
    * @return
    *   Promise<null | string | string[]>
    */
  def open(
      options: OpenDialogOptions
  ): js.Promise[scala.Null | String | js.Array[String]] = js.native

  /** Extension filters for the file dialog.
    * https://v2.tauri.app/reference/javascript/dialog/#dialogfilter
    */
  trait DialogFilter extends js.Object {

    /** Extensions to filter, without a . prefix.
      */
    val extensions: js.Array[String]

    /** Filter name.
      */
    val name: String
  }

  /** Options for the open dialog.
    * https://v2.tauri.app/reference/javascript/dialog/#opendialogoptions
    */
  trait OpenDialogOptions extends js.Object {

    /** Whether to allow creating directories in the dialog. Enabled by default.
      * macOS Only.
      */
    val canCreateDirectories: js.UndefOr[Boolean] = js.undefined

    /** Initial directory or file path. If it’s a directory path, the dialog
      * interface will change to that folder. If it’s not an existing directory,
      * the file name will be set to the dialog’s file name input and the dialog
      * will be set to the parent folder.
      */
    val defaultPath: js.UndefOr[String] = js.undefined

    /** Whether the dialog is a directory selection or not.
      */
    val directory: js.UndefOr[Boolean] = js.undefined

    /** The filters of the dialog.
      */
    val filters: js.UndefOr[js.Array[DialogFilter]] = js.undefined

    /** Whether the dialog allows multiple selection or not.
      */
    val multiple: js.UndefOr[Boolean] = js.undefined

    /** If directory is true, indicates that it will be read recursively later.
      * Defines whether subdirectories will be allowed on the scope or not.
      */
    val recursive: js.UndefOr[Boolean] = js.undefined

    /** The title of the dialog window (desktop only).
      */
    val title: js.UndefOr[String] = js.undefined
  }
}
