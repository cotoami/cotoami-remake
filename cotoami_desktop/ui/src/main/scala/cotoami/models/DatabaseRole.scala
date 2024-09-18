package cotoami.models

import java.time.Instant

sealed trait DatabaseRole

object DatabaseRole {
  case class Parent(info: ParentNode) extends DatabaseRole
  case class Child(info: ChildNode) extends DatabaseRole

  import cotoami.backend.DatabaseRoleJson

  def apply(json: DatabaseRoleJson): DatabaseRole = {
    for (parent <- json.Parent.toOption) {
      return Parent(ParentNode(parent))
    }
    for (child <- json.Child.toOption) {
      return Child(ChildNode(child))
    }
    return null // this should be unreachable
  }
}

case class ParentNode(
    nodeId: Id[Node],
    createdAt: Instant,
    changesReceived: Double,
    lastChangeReceivedAt: Option[Instant],
    forked: Boolean
)

object ParentNode {
  import cotoami.backend.{parseJsonDateTime, Nullable, ParentNodeJson}

  def apply(json: ParentNodeJson): ParentNode =
    ParentNode(
      Id(json.node_id),
      parseJsonDateTime(json.created_at),
      json.changes_received,
      Nullable.toOption(json.last_change_received_at).map(parseJsonDateTime),
      json.forked
    )
}

case class ChildNode(
    nodeId: Id[Node],
    createdAt: Instant,
    asOwner: Boolean,
    canEditLinks: Boolean
)

object ChildNode {
  import cotoami.backend.{parseJsonDateTime, ChildNodeJson}

  def apply(json: ChildNodeJson): ChildNode =
    ChildNode(
      Id(json.node_id),
      parseJsonDateTime(json.created_at),
      json.as_owner,
      json.can_edit_links
    )
}
