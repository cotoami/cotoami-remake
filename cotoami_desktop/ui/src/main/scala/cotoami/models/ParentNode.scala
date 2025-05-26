package cotoami.models

import java.time.Instant

case class ParentNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    changesReceived: Double,
    lastChangeReceivedAtUtcIso: Option[String],
    lastReadAtUtcIso: Option[String],
    forked: Boolean,
    lastPostedAtByOthersUtcIso: Option[String] = None
) {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val lastChangeReceivedAt: Option[Instant] =
    lastChangeReceivedAtUtcIso.map(parseUtcIso)
  lazy val lastReadAt: Option[Instant] =
    lastReadAtUtcIso.map(parseUtcIso)
  lazy val lastPostedAtByOthers: Option[Instant] =
    lastPostedAtByOthersUtcIso.map(parseUtcIso)

  def anyUnreadPosts: Boolean =
    (lastPostedAtByOthers, lastReadAt) match {
      case (Some(posted), Some(read)) => posted.isAfter(read)
      case (Some(_), None)            => true
      case _                          => false
    }
}
