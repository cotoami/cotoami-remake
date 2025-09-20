package marubinotto.libs.geomap

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("@protomaps/basemaps", JSImport.Namespace)
object basemaps extends js.Object {

  def namedFlavor(name: String): Flavor = js.native

  def layers(
      source: String,
      flavor: Flavor,
      options: js.UndefOr[LayersOptions]
  ): js.Any = js.native

  @js.native
  trait Flavor extends js.Object {
    val address_label: Double = js.native
  }

  trait LayersOptions extends js.Object {
    val labelsOnly: js.UndefOr[Boolean] = js.undefined
    val lang: js.UndefOr[String] = js.undefined
  }
}
