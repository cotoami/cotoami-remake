package cotoami.models

import java.time.Instant

case class ParentNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    changesReceived: Double,
    lastChangeReceivedAtUtcIso: Option[String],
    lastReadAtUtcIso: Option[String],
    forked: Boolean,
    othersLastPostedAtUtcIso: Option[String] = None
) extends ReadTrackableNode {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val lastChangeReceivedAt: Option[Instant] =
    lastChangeReceivedAtUtcIso.map(parseUtcIso)
}
