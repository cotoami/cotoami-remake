package cotoami.models

import java.time.Instant

case class ChildNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    asOwner: Boolean,
    _canEditItos: Boolean,
    _canPostCotonomas: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)

  def canEditItos: Boolean = asOwner || _canEditItos

  def canPostCotonomas: Boolean = asOwner || _canPostCotonomas
}
