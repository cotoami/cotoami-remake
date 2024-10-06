package cotoami.models

import cotoami.utils.Validation

case class ClientNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    sessionExpiresAtUtcIso: Option[String],
    disabled: Boolean
)

object ClientNode {
  def validateNodeId(nodeId: String): Seq[Validation.Error] = {
    val fieldName = "node ID"
    Seq(
      Validation.nonBlank(fieldName, nodeId),
      Validation.uuid(fieldName, nodeId)
    ).flatten
  }
}

case class ActiveClient(
    nodeId: Id[Node],
    remoteAddr: String
)
