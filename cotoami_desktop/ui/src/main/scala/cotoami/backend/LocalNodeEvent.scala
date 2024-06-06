package cotoami.backend

import scala.scalajs.js

@js.native
trait LocalNodeEventJson extends js.Object {
  val ServerDisconnected: js.UndefOr[ServerDisconnectedJson] = js.native
  val ParentSyncStart: js.UndefOr[ParentSyncStartJson] = js.native
  val ParentSyncProgress: js.UndefOr[ParentSyncProgressJson] = js.native
  val ParentSyncEnd: js.UndefOr[ParentSyncEndJson] = js.native
  val ParentDisconnected: js.UndefOr[String] = js.native
}

@js.native
trait ServerDisconnectedJson extends js.Object {
  val node_id: String = js.native
  val reason: NotConnectedJson = js.native
}

@js.native
trait ParentSyncStartJson extends js.Object {
  val node_id: String = js.native
  val parent_description: String = js.native
}

@js.native
trait ParentSyncProgressJson extends js.Object {
  val node_id: String = js.native
  val progress: Double = js.native
  val max: Double = js.native
}

@js.native
trait ParentSyncEndJson extends js.Object {
  val node_id: String = js.native
  val range: Nullable[js.Tuple2[Double, Double]] = js.native
  val error: Nullable[String] = js.native
}
