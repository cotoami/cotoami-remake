package cotoami.backend

import scala.scalajs.js

import cotoami.utils.facade.Nullable
import cotoami.models.{ChildNode, DatabaseRole, Id, ParentNode}

@js.native
trait DatabaseRoleJson extends js.Object {
  val Parent: js.UndefOr[ParentNodeJson] = js.native
  val Child: js.UndefOr[ChildNodeJson] = js.native
}

object DatabaseRoleBackend {
  def toModel(json: DatabaseRoleJson): DatabaseRole = {
    for (parent <- json.Parent.toOption) {
      return DatabaseRole.Parent(ParentNodeBackend.toModel(parent))
    }
    for (child <- json.Child.toOption) {
      return DatabaseRole.Child(ChildNodeBackend.toModel(child))
    }
    return null // this should be unreachable
  }
}

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

@js.native
trait ChildNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val as_owner: Boolean = js.native
  val can_edit_itos: Boolean = js.native
}

object ChildNodeBackend {
  def toModel(json: ChildNodeJson): ChildNode =
    ChildNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      asOwner = json.as_owner,
      canEditItos = json.can_edit_itos
    )
}
