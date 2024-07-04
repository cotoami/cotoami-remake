package cotoami.backend

import scala.scalajs.js
import java.time.Instant
import cotoami.utils.Validation

case class Server(
    server: ServerNode,
    role: Option[DatabaseRole],
    clientAsChild: Option[ChildNode],
    notConnected: Option[NotConnected]
)

object Server {
  def apply(json: ServerJson): Server =
    Server(
      ServerNode(json.server),
      Nullable.toOption(json.role).map(DatabaseRole(_)),
      Nullable.toOption(json.client_as_child).map(ChildNode(_)),
      Nullable.toOption(json.not_connected).map(NotConnected(_))
    )
}

@js.native
trait ServerJson extends js.Object {
  val server: ServerNodeJson = js.native
  val role: Nullable[DatabaseRoleJson] = js.native
  val client_as_child: Nullable[ChildNodeJson] = js.native
  val not_connected: Nullable[NotConnectedJson] = js.native
}

case class ServerNode(json: ServerNodeJson) {
  def nodeId: Id[Node] = Id(this.json.node_id)
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  def urlPrefix: String = this.json.url_prefix
  def disabled: Boolean = this.json.disabled
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

@js.native
trait ServerNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val url_prefix: String = js.native
  val disabled: Boolean = js.native
}

sealed trait NotConnected

object NotConnected {
  def apply(json: NotConnectedJson): NotConnected = json.reason match {
    case "Disabled"     => Disabled
    case "Connecting"   => Connecting(Option(json.details))
    case "InitFailed"   => InitFailed(json.details)
    case "Disconnected" => Disconnected(Option(json.details))
  }

  case object Disabled extends NotConnected
  case class Connecting(details: Option[String]) extends NotConnected
  case class InitFailed(details: String) extends NotConnected
  case class Disconnected(details: Option[String]) extends NotConnected
}

@js.native
trait NotConnectedJson extends js.Object {
  val reason: String = js.native
  val details: String = js.native
}
