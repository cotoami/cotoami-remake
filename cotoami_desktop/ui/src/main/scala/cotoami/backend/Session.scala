package cotoami.backend

import scala.scalajs.js
import java.time.Instant

import fui.Cmd

case class Session(json: SessionJson) {
  def token: String = this.json.token
  lazy val expiresAt: Instant = parseJsonDateTime(this.json.expires_at)
}

@js.native
trait SessionJson extends js.Object {
  val token: String = js.native
  val expires_at: String = js.native
}

case class ClientNodeSession(json: ClientNodeSessionJson) {
  def session: Session = Session(this.json.session)
  def server: Node = Node(this.json.server)
  def serverRootCotonoma: Option[(Cotonoma, Coto)] =
    Nullable.toOption(this.json.server_root_cotonoma).map(pair =>
      (Cotonoma(pair._1), Coto(pair._2))
    )
  def asChild: Option[ChildNode] =
    Nullable.toOption(this.json.as_child).map(ChildNode(_))
}

object ClientNodeSession {
  def logIntoServer(
      url: String,
      password: String
  ): Cmd[Either[ErrorJson, ClientNodeSession]] =
    ClientNodeSessionJson.logIntoServer(url, password)
      .map(_.map(ClientNodeSession(_)))
}

@js.native
trait ClientNodeSessionJson extends js.Object {
  val session: SessionJson = js.native
  val server: NodeJson = js.native
  val server_root_cotonoma: Nullable[js.Tuple2[CotonomaJson, CotoJson]] =
    js.native
  val as_child: Nullable[ChildNodeJson] = js.native
}

object ClientNodeSessionJson {
  def logIntoServer(
      url: String,
      password: String
  ): Cmd[Either[ErrorJson, ClientNodeSessionJson]] =
    Commands.send(Commands.TryLogIntoServer(url, password))
}
