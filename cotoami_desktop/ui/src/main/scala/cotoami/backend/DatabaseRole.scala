package cotoami.backend

import scala.scalajs.js

@js.native
trait DatabaseRoleJson extends js.Object {
  val Parent: js.UndefOr[ParentNodeJson] = js.native
  val Child: js.UndefOr[ChildNodeJson] = js.native
}

@js.native
trait ParentNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val changes_received: Double = js.native
  val last_change_received_at: Nullable[String] = js.native
  val forked: Boolean = js.native
}

@js.native
trait ChildNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val as_owner: Boolean = js.native
  val can_edit_links: Boolean = js.native
}
