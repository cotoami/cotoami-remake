package cotoami.backend

import scala.scalajs.js
import java.time.Instant

import fui.Cmd
import cotoami.utils.facade.Nullable
import cotoami.models.{ChildNode, Coto, Cotonoma, Node}

case class SessionToken(json: SessionTokenJson) {
  def token: String = json.token
  lazy val expiresAt: Instant = parseJsonDateTime(json.expires_at)
}

@js.native
trait SessionTokenJson extends js.Object {
  val token: String = js.native
  val expires_at: String = js.native
}

case class ClientNodeSession(json: ClientNodeSessionJson) {
  def token: Option[SessionToken] =
    Nullable.toOption(json.token).map(SessionToken)
  def server: Node = NodeBackend.toModel(json.server)
  def serverRoot: Option[(Cotonoma, Coto)] =
    Nullable.toOption(json.server_root).map(pair =>
      (CotonomaBackend.toModel(pair._1), CotoBackend.toModel(pair._2))
    )
  def asChild: Option[ChildNode] =
    Nullable.toOption(json.as_child).map(ChildNodeBackend.toModel)
}

object ClientNodeSession {
  def logIntoServer(
      url: String,
      password: Option[String]
  ): Cmd.One[Either[ErrorJson, ClientNodeSession]] =
    ClientNodeSessionJson.logIntoServer(url, password)
      .map(_.map(ClientNodeSession(_)))
}

@js.native
trait ClientNodeSessionJson extends js.Object {
  val token: Nullable[SessionTokenJson] = js.native
  val server: NodeJson = js.native
  val server_root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] =
    js.native
  val as_child: Nullable[ChildNodeJson] = js.native
}

object ClientNodeSessionJson {
  def logIntoServer(
      url: String,
      password: Option[String]
  ): Cmd.One[Either[ErrorJson, ClientNodeSessionJson]] =
    Commands.send(Commands.TryLogIntoServer(url, password))
}
