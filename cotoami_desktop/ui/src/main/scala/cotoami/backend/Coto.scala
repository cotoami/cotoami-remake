package cotoami.backend

import scala.scalajs.js
import cotoami.Id

case class Coto(json: CotoJson) {
  def id(): Id[Coto] = Id(this.json.uuid)
}

@js.native
trait CotoJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val posted_in_id: String = js.native
  val posted_by_id: String = js.native
  val content: String = js.native
  val summary: String = js.native
  val is_cotonoma: Boolean = js.native
  val repost_of_id: String = js.native
  val reposted_in_ids: js.Array[String] = js.native
}
