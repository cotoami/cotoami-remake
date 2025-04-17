package cotoami.models

case class LocalNode(
    nodeId: Id[Node],
    imageMaxSize: Option[Int],
    anonymousReadEnabled: Boolean
)
