package cotoami.libs.geomap

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

package object maplibre {

  /** Adds a custom load resource function that will be called when using a URL
    * that starts with a custom url schema. This will happen in the main thread,
    * and workers might call it if they don't know how to handle the protocol.
    */
  @js.native
  @JSImport("maplibre-gl", "addProtocol")
  def addProtocol(customProtocol: String, loadFn: js.Any): Unit = js.native
}
