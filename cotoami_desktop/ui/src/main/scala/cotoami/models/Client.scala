package cotoami.models

import cotoami.utils.Validation

case class ClientNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    sessionExpiresAtUtcIso: Option[String],
    disabled: Boolean
)

object ClientNode {
  def validateNodeId(
      nodeId: String,
      localNodeId: Option[Id[Node]]
  ): Seq[Validation.Error] = {
    val fieldName = "node ID"
    Seq(
      Validation.nonBlank(fieldName, nodeId),
      Validation.uuid(fieldName, nodeId)
    ).flatten match {
      case Seq() =>
        if (Some(nodeId) == localNodeId.map(_.uuid))
          Seq(Node.localNodeCannotBeRemoteError)
        else
          Seq.empty
      case errors => errors
    }
  }
}

case class ActiveClient(
    nodeId: Id[Node],
    remoteAddr: String
)
