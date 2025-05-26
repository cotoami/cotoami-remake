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
) {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val lastChangeReceivedAt: Option[Instant] =
    lastChangeReceivedAtUtcIso.map(parseUtcIso)
  lazy val lastReadAt: Option[Instant] =
    lastReadAtUtcIso.map(parseUtcIso)
  lazy val othersLastPostedAt: Option[Instant] =
    othersLastPostedAtUtcIso.map(parseUtcIso)

  def anyUnreadPosts: Boolean =
    (othersLastPostedAt, lastReadAt) match {
      case (Some(posted), Some(read)) => posted.isAfter(read)
      case (Some(_), None)            => true
      case _                          => false
    }
}
