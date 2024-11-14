package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.utils.facade.Nullable
import cotoami.models.{Id, Node, NotConnected, Server, ServerNode}

@js.native
trait ServerJson extends js.Object {
  val server: ServerNodeJson = js.native
  val role: Nullable[DatabaseRoleJson] = js.native
  val not_connected: Nullable[NotConnectedJson] = js.native
  val client_as_child: Nullable[ChildNodeJson] = js.native
}

object ServerJson {
  def addServer(
      url: String,
      password: String,
      clientRole: Option[String] = None
  ): Cmd.One[Either[ErrorJson, ServerJson]] =
    Commands.send(Commands.AddServer(url, password, clientRole))
}

object ServerBackend {
  def toModel(json: ServerJson): Server =
    Server(
      ServerNodeBackend.toModel(json.server),
      Nullable.toOption(json.role).map(DatabaseRoleBackend.toModel(_)),
      Nullable.toOption(json.not_connected).map(NotConnectedBackend.toModel(_)),
      Nullable.toOption(json.client_as_child).map(ChildNodeBackend.toModel(_))
    )

  def addServer(
      url: String,
      password: String,
      clientRole: Option[String] = None
  ): Cmd.One[Either[ErrorJson, Server]] =
    ServerJson.addServer(url, password, clientRole)
      .map(_.map(toModel(_)))
}

@js.native
trait ServerNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val url_prefix: String = js.native
  val disabled: Boolean = js.native
}

object ServerNodeJson {
  def update(
      id: Id[Node],
      disabled: Option[Boolean],
      url: Option[String]
  ): Cmd.One[Either[ErrorJson, ServerNodeJson]] =
    Commands.send(Commands.UpdateServer(id, disabled, url))
}

object ServerNodeBackend {
  def toModel(json: ServerNodeJson): ServerNode =
    ServerNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      urlPrefix = json.url_prefix,
      disabled = json.disabled
    )

  def update(
      id: Id[Node],
      disabled: Option[Boolean],
      url: Option[String]
  ): Cmd.One[Either[ErrorJson, ServerNode]] =
    ServerNodeJson.update(id, disabled, url)
      .map(_.map(toModel(_)))
}

@js.native
trait NotConnectedJson extends js.Object {
  val reason: String = js.native
  val details: String = js.native
}

object NotConnectedBackend {
  def toModel(json: NotConnectedJson): NotConnected = json.reason match {
    case "Disabled"     => NotConnected.Disabled
    case "Connecting"   => NotConnected.Connecting(Option(json.details))
    case "InitFailed"   => NotConnected.InitFailed(json.details)
    case "Disconnected" => NotConnected.Disconnected(Option(json.details))
  }
}
