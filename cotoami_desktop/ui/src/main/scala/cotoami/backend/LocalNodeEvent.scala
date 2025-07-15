package cotoami.backend

import scala.scalajs.js
import marubinotto.facade.Nullable
import cotoami.models.{Id, ParentSyncEnd, ParentSyncProgress}

@js.native
trait LocalNodeEventJson extends js.Object {
  import LocalNodeEventJson._

  val ServerStateChanged: js.UndefOr[ServerStateChanged] = js.native
  val ClientConnected: js.UndefOr[ActiveClientJson] = js.native
  val ClientDisconnected: js.UndefOr[ClientDisconnected] = js.native
  val ParentRegistered: js.UndefOr[ParentRegistered] = js.native
  val ParentSyncStart: js.UndefOr[ParentSyncStart] = js.native
  val ParentSyncProgress: js.UndefOr[LocalNodeEventJson.ParentSyncProgress] =
    js.native
  val ParentSyncEnd: js.UndefOr[LocalNodeEventJson.ParentSyncEnd] = js.native
  val ParentDisconnected: js.UndefOr[ParentDisconnected] =
    js.native
  val PluginEvent: js.UndefOr[PluginEventJson] = js.native
}

object LocalNodeEventJson {
  @js.native
  trait ServerStateChanged extends js.Object {
    val node_id: String = js.native
    val not_connected: Nullable[NotConnectedJson] = js.native
    val child_privileges: Nullable[ChildNodeJson] = js.native
  }

  @js.native
  trait ClientDisconnected extends js.Object {
    val node_id: String = js.native
    val error: Nullable[String] = js.native
  }

  @js.native
  trait ParentRegistered extends js.Object {
    val node_id: String = js.native
  }

  @js.native
  trait ParentSyncStart extends js.Object {
    val node_id: String = js.native
    val parent_description: String = js.native
  }

  @js.native
  trait ParentSyncProgress extends js.Object {
    val node_id: String = js.native
    val progress: Double = js.native
    val total: Double = js.native
  }

  @js.native
  trait ParentSyncEnd extends js.Object {
    val node_id: String = js.native
    val range: Nullable[js.Tuple2[Double, Double]] = js.native
    val error: Nullable[String] = js.native
  }

  @js.native
  trait ParentDisconnected extends js.Object {
    val node_id: String = js.native
  }
}

object ParentSyncProgressBackend {
  def toModel(json: LocalNodeEventJson.ParentSyncProgress): ParentSyncProgress =
    ParentSyncProgress(
      nodeId = Id(json.node_id),
      progress = json.progress,
      total = json.total
    )
}

object ParentSyncEndBackend {
  def toModel(json: LocalNodeEventJson.ParentSyncEnd): ParentSyncEnd =
    ParentSyncEnd(
      nodeId = Id(json.node_id),
      range = Nullable.toOption(json.range).map(js.Tuple2.toScalaTuple2),
      error = Nullable.toOption(json.error)
    )
}
