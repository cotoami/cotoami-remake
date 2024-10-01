package cotoami.models

case class ClientNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    sessionExpiresAtUtcIso: Option[String],
    disabled: Boolean
)

case class ActiveClient(
    nodeId: Id[Node],
    remoteAddr: String
)
