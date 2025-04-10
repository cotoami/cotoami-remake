package cotoami.backend

import scala.scalajs.js

import marubinotto.facade.Nullable
import cotoami.models.{Id, ParentNode}

@js.native
trait ParentNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val changes_received: Double = js.native
  val last_change_received_at: Nullable[String] = js.native
  val forked: Boolean = js.native
}

object ParentNodeBackend {
  def toModel(json: ParentNodeJson): ParentNode =
    ParentNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      changesReceived = json.changes_received,
      lastChangeReceivedAtUtcIso =
        Nullable.toOption(json.last_change_received_at),
      forked = json.forked
    )
}
