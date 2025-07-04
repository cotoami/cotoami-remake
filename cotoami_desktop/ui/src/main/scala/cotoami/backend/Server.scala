package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable
import cotoami.models.{Id, Node, Server, ServerNode}

@js.native
trait ServerJson extends js.Object {
  val server: ServerNodeJson = js.native
  val role: Nullable[DatabaseRoleJson] = js.native
  val not_connected: Nullable[NotConnectedJson] = js.native
  val child_privileges: Nullable[ChildNodeJson] = js.native
}

object ServerJson {
  def addServer(
      url: String,
      password: Option[String],
      clientRole: Option[String] = None
  ): Cmd.One[Either[ErrorJson, ServerJson]] =
    Commands.send(Commands.AddServer(url, password, clientRole))
}

object ServerBackend {
  def toModel(json: ServerJson): Server =
    Server(
      ServerNodeBackend.toModel(json.server),
      Nullable.toOption(json.role).map(DatabaseRoleBackend.toModel),
      Nullable.toOption(json.not_connected).map(NotConnectedBackend.toModel),
      Nullable.toOption(json.child_privileges).map(ChildNodeBackend.toModel)
    )

  def addServer(
      url: String,
      password: Option[String],
      clientRole: Option[String] = None
  ): Cmd.One[Either[ErrorJson, Server]] =
    ServerJson.addServer(url, password, clientRole)
      .map(_.map(toModel))
}

@js.native
trait ServerNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val url_prefix: String = js.native
  val disabled: Boolean = js.native
}

object ServerNodeJson {
  def edit(
      id: Id[Node],
      disabled: Option[Boolean],
      password: Option[String],
      url: Option[String]
  ): Cmd.One[Either[ErrorJson, ServerNodeJson]] =
    Commands.send(Commands.EditServer(id, disabled, password, url))
}

object ServerNodeBackend {
  def toModel(json: ServerNodeJson): ServerNode =
    ServerNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      urlPrefix = json.url_prefix,
      disabled = json.disabled
    )

  def edit(
      id: Id[Node],
      disabled: Option[Boolean],
      password: Option[String],
      url: Option[String]
  ): Cmd.One[Either[ErrorJson, ServerNode]] =
    ServerNodeJson.edit(id, disabled, password, url).map(_.map(toModel))
}

@js.native
trait NotConnectedJson extends js.Object {
  val Connecting: js.UndefOr[Nullable[String]] = js.native
  val InitFailed: js.UndefOr[String] = js.native
  val Disconnected: js.UndefOr[Nullable[String]] = js.native

  // Unit variants are represented as strings in JSON.
  // https://serde.rs/json.html
  //
  // - "Disabled"
  // - "Unauthorized"
  // - "SessionExpired"
}

object NotConnectedBackend {
  def toModel(json: NotConnectedJson): Server.NotConnected = {
    if (json.toString == "Disabled") {
      return Server.NotConnected.Disabled
    }
    for (msg <- json.Connecting.toOption) {
      return Server.NotConnected.Connecting(Nullable.toOption(msg))
    }
    for (msg <- json.InitFailed.toOption) {
      return Server.NotConnected.InitFailed(msg)
    }
    if (json.toString == "Unauthorized") {
      return Server.NotConnected.Unauthorized
    }
    if (json.toString == "SessionExpired") {
      return Server.NotConnected.SessionExpired
    }
    for (msg <- json.Disconnected.toOption) {
      return Server.NotConnected.Disconnected(Nullable.toOption(msg))
    }
    Server.NotConnected.Disconnected(
      Some(s"Invalid NotConnected JSON : ${js.JSON.stringify(json)}")
    )
  }
}
