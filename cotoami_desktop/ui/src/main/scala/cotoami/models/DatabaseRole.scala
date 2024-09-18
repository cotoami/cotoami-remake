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
    createdAtUtcIso: String,
    changesReceived: Double,
    lastChangeReceivedAtUtcIso: Option[String],
    forked: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)
  lazy val lastChangeReceivedAt: Option[Instant] =
    this.lastChangeReceivedAtUtcIso.map(parseUtcIso)
}

object ParentNode {
  import cotoami.backend.{Nullable, ParentNodeJson}

  def apply(json: ParentNodeJson): ParentNode =
    ParentNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      changesReceived = json.changes_received,
      lastChangeReceivedAtUtcIso =
        Nullable.toOption(json.last_change_received_at),
      forked = json.forked
    )
}

case class ChildNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    asOwner: Boolean,
    canEditLinks: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)
}

object ChildNode {
  import cotoami.backend.ChildNodeJson

  def apply(json: ChildNodeJson): ChildNode =
    ChildNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      asOwner = json.as_owner,
      canEditLinks = json.can_edit_links
    )
}
