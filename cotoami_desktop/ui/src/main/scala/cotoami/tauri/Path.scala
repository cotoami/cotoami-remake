package cotoami.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@tauri-apps/api/path", JSImport.Namespace)
object Path extends js.Object {

  // The platform-specific path segment delimiter.
  val delimiter: String = js.native

  // The platform-specific path segment separator.
  val sep: String = js.native

  // Resolve the path to a resource file.
  def resolveResource(resourcePath: String): js.Promise[String] = js.native

  // Returns the path to the application's resource directory.
  def resourceDir: js.Promise[String] = js.native
}
