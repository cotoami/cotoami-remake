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

  def orientation(file: dom.Blob | String): js.Promise[js.UndefOr[Int]] =
    js.native

  def rotation(file: dom.Blob | String): js.Promise[js.UndefOr[Rotation]] =
    js.native

  @js.native
  trait Rotation extends js.Object {
    val deg: Double = js.native
    val rad: Double = js.native
    val scaleX: Int = js.native
    val scaleY: Int = js.native
    val dimensionSwapped: Boolean = js.native
    val css: Boolean = js.native
    val canvas: Boolean = js.native
  }
}
