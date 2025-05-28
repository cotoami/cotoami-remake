package cotoami.models

case class LocalNode(
    nodeId: Id[Node],
    imageMaxSize: Option[Int],
    anonymousReadEnabled: Boolean,
    lastReadAtUtcIso: Option[String],
    othersLastPostedAtUtcIso: Option[String] = None
) extends ReadTrackableNode
