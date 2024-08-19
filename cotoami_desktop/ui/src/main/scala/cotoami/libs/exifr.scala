package cotoami.libs

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

// https://github.com/MikeKovarik/exifr
@js.native
@JSImport("exifr/dist/lite.esm.mjs", JSImport.Default)
object exifr extends js.Object {

  // Only extracts GPS coordinates.
  def gps(file: dom.Blob | String): js.Promise[js.UndefOr[Gps]] = js.native

  @js.native
  trait Gps extends js.Object {
    val latitude: Double = js.native
    val longitude: Double = js.native
  }
}
