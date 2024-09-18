package cotoami.models

import java.time.Instant

sealed trait DatabaseRole

object DatabaseRole {
  case class Parent(info: ParentNode) extends DatabaseRole
  case class Child(info: ChildNode) extends DatabaseRole
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

case class ChildNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    asOwner: Boolean,
    canEditLinks: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)
}
