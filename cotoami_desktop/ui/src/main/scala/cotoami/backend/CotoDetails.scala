package cotoami.backend

import scala.scalajs.js

@js.native
trait CotoDetailsJson extends js.Object {
  val coto: CotoJson = js.native
  val related_data: CotosRelatedDataJson = js.native
  val outgoing_links: js.Array[LinkJson] = js.native
}
