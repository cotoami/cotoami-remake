package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** The path module provides utilities for working with file and directory
  * paths.
  *
  * https://v2.tauri.app/reference/javascript/api/namespacepath/
  */
@js.native
@JSImport("@tauri-apps/api/path", JSImport.Namespace)
object path extends js.Object {

  /** Returns the platform-specific path segment delimiter.
    */
  def delimiter(): String = js.native

  /** Returns the platform-specific path segment separator.
    */
  def sep(): String = js.native

  /** Resolve the path to a resource file.
    *
    * @param resourcePath
    *   The path to the resource. Must follow the same syntax as defined in
    *   tauri.conf.json > bundle > resources, i.e. keeping subfolders and parent
    *   dir components (../).
    */
  def resolveResource(resourcePath: String): js.Promise[String] = js.native

  /** Returns the path to the application’s resource directory.
    */
  def resourceDir(): js.Promise[String] = js.native
}
