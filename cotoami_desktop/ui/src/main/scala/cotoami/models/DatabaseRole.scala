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
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val lastChangeReceivedAt: Option[Instant] =
    lastChangeReceivedAtUtcIso.map(parseUtcIso)
}

sealed trait ParentStatus {
  def disabled: Boolean =
    this match {
      case ParentStatus.ServerDisconnected(Server.NotConnected.Disabled) => true
      case _ => false
    }
}

object ParentStatus {
  case class Connected(child: Option[ChildNode]) extends ParentStatus
  case class ServerDisconnected(details: Server.NotConnected)
      extends ParentStatus
}

case class ChildNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    asOwner: Boolean,
    canEditItos: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
}
