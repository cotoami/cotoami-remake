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
      Id(json.node_id),
      json.created_at,
      json.changes_received,
      Nullable.toOption(json.last_change_received_at),
      json.forked
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
      Id(json.node_id),
      json.created_at,
      json.as_owner,
      json.can_edit_links
    )
}
