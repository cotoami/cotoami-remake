package cotoami.models

import java.time.Instant

sealed trait DatabaseRole

object DatabaseRole {
  case class Parent(info: ParentNode) extends DatabaseRole
  case class Child(info: ChildNode) extends DatabaseRole
}

case class ChildNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    asOwner: Boolean,
    canEditItos: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
}
