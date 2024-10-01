package cotoami.backend

import scala.scalajs.js
import cotoami.models.{Id, ParentSyncEnd, ParentSyncProgress}

@js.native
trait LocalNodeEventJson extends js.Object {
  val ServerStateChanged: js.UndefOr[ServerStateChangedJson] = js.native
  val ParentSyncStart: js.UndefOr[ParentSyncStartJson] = js.native
  val ParentSyncProgress: js.UndefOr[ParentSyncProgressJson] = js.native
  val ParentSyncEnd: js.UndefOr[ParentSyncEndJson] = js.native
  val ParentDisconnected: js.UndefOr[ParentDisconnectedJson] = js.native
  val ClientConnected: js.UndefOr[ActiveClientJson] = js.native
  val ClientDisconnected: js.UndefOr[ClientDisconnectedJson] = js.native
}

@js.native
trait ServerStateChangedJson extends js.Object {
  val node_id: String = js.native
  val not_connected: Nullable[NotConnectedJson] = js.native
  val client_as_child: Nullable[ChildNodeJson] = js.native
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
  val total: Double = js.native
}

object ParentSyncProgressBackend {
  def toModel(json: ParentSyncProgressJson): ParentSyncProgress =
    ParentSyncProgress(
      nodeId = Id(json.node_id),
      progress = json.progress,
      total = json.total
    )
}

@js.native
trait ParentSyncEndJson extends js.Object {
  val node_id: String = js.native
  val range: Nullable[js.Tuple2[Double, Double]] = js.native
  val error: Nullable[String] = js.native
}

object ParentSyncEndBackend {
  def toModel(json: ParentSyncEndJson): ParentSyncEnd =
    ParentSyncEnd(
      nodeId = Id(json.node_id),
      range = Nullable.toOption(json.range).map(js.Tuple2.toScalaTuple2(_)),
      error = Nullable.toOption(json.error)
    )
}

@js.native
trait ParentDisconnectedJson extends js.Object {
  val node_id: String = js.native
}

@js.native
trait ClientDisconnectedJson extends js.Object {
  val node_id: String = js.native
  val error: Nullable[String] = js.native
}
