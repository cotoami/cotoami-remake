package marubinotto.libs.geomap

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("pmtiles", JSImport.Namespace)
object pmtiles extends js.Object {

  @js.native
  class Protocol extends js.Object {
    val tile: js.Any = js.native
  }
}
