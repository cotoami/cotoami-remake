package cotoami.backend

import scala.scalajs.js
import cotoami.models.{Id, Node}

@js.native
trait LocalNodeEventJson extends js.Object {
  val ServerStateChanged: js.UndefOr[ServerStateChangedJson] = js.native
  val ParentSyncStart: js.UndefOr[ParentSyncStartJson] = js.native
  val ParentSyncProgress: js.UndefOr[ParentSyncProgressJson] = js.native
  val ParentSyncEnd: js.UndefOr[ParentSyncEndJson] = js.native
  val ParentDisconnected: js.UndefOr[String] = js.native
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

case class ParentSyncProgress(json: ParentSyncProgressJson) {
  def nodeId: Id[Node] = Id(this.json.node_id)
  def progress: Double = this.json.progress
  def total: Double = this.json.total
}

@js.native
trait ParentSyncProgressJson extends js.Object {
  val node_id: String = js.native
  val progress: Double = js.native
  val total: Double = js.native
}

case class ParentSyncEnd(json: ParentSyncEndJson) {
  def nodeId: Id[Node] = Id(this.json.node_id)
  def range: Option[(Double, Double)] =
    Nullable.toOption(this.json.range).map(js.Tuple2.toScalaTuple2(_))
  def error: Option[String] = Nullable.toOption(this.json.error)

  def noChanges: Boolean = this.range.isEmpty && this.error.isEmpty
}

@js.native
trait ParentSyncEndJson extends js.Object {
  val node_id: String = js.native
  val range: Nullable[js.Tuple2[Double, Double]] = js.native
  val error: Nullable[String] = js.native
}
