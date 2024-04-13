package cotoami.backend

import scala.scalajs.js

@js.native
trait LinkJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val created_in_id: String = js.native
  val created_by_id: String = js.native
  val source_coto_id: String = js.native
  val target_coto_id: String = js.native
  val linking_phrase: String = js.native
  val details: String = js.native
  val order: Int = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
}
