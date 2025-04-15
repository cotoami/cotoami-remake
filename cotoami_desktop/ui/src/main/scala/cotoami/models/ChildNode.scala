package cotoami.models

import java.time.Instant

case class ChildNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    asOwner: Boolean,
    canEditItos: Boolean,
    canPostCotonomas: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
}
