package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js
import java.time.Instant

import marubinotto.facade.Nullable
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

case class ClientNodeSession(
    json: ClientNodeSessionJson,
    server: Node,
    serverRoot: Option[(Cotonoma, Coto)]
) {
  def token: Option[SessionToken] =
    Nullable.toOption(json.token).map(SessionToken.apply)
  def childPrivileges: Option[ChildNode] =
    Nullable.toOption(json.child_privileges).map(ChildNodeBackend.toModel)
}

object ClientNodeSession {
  def decode(json: ClientNodeSessionJson): IO[ClientNodeSession] =
    for {
      server <- NodeBackend.toModel(json.server)
      serverRoot <- Nullable.toOption(json.server_root).traverse(
        CotonomaBackend.decodePair
      )
    } yield ClientNodeSession(json, server, serverRoot)

  def logIntoServer(
      url: String,
      password: Option[String]
  ): IO[Either[ErrorJson, ClientNodeSession]] =
    ClientNodeSessionJson.logIntoServer(url, password)
      .flatMap(_.traverse(ClientNodeSession.decode))
}

@js.native
trait ClientNodeSessionJson extends js.Object {
  val token: Nullable[SessionTokenJson] = js.native
  val server: NodeJson = js.native
  val server_root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] =
    js.native
  val child_privileges: Nullable[ChildNodeJson] = js.native
}

object ClientNodeSessionJson {
  def logIntoServer(
      url: String,
      password: Option[String]
  ): IO[Either[ErrorJson, ClientNodeSessionJson]] =
    Commands.send(Commands.TryLogIntoServer(url, password))
}
