package cotoami.models

import java.time.Instant
import marubinotto.Validation

case class Server(
    server: ServerNode,
    role: Option[DatabaseRole],
    notConnected: Option[Server.NotConnected],
    childPrivileges: Option[ChildNode]
)

object Server {
  sealed trait NotConnected
  object NotConnected {
    case object Disabled extends NotConnected
    case class Connecting(details: Option[String]) extends NotConnected
    case class InitFailed(details: String) extends NotConnected
    case object Unauthorized extends NotConnected
    case object SessionExpired extends NotConnected
    case class Disconnected(details: Option[String]) extends NotConnected
  }
}

case class ServerNode(
    nodeId: Id[Node],
    createdAtUtcIso: String,
    urlPrefix: String,
    disabled: Boolean
) {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
}

object ServerNode {
  final val UrlMaxLength = 1500

  def validateUrl(url: String): Seq[Validation.Error] = {
    val fieldName = "node URL"
    Seq(
      Validation.nonBlank(fieldName, url),
      Validation.length(fieldName, url, 1, UrlMaxLength),
      Validation.httpUrl(fieldName, url)
    ).flatten
  }
}
