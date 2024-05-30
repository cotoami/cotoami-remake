package cotoami.backend

import scala.scalajs.js
import java.time.Instant

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
    Option(this.json.server_root_cotonoma).map(pair =>
      (Cotonoma(pair._1), Coto(pair._2))
    )
}

@js.native
trait ClientNodeSessionJson extends js.Object {
  val session: SessionJson = js.native
  val server: NodeJson = js.native
  val server_root_cotonoma: js.Tuple2[CotonomaJson, CotoJson] = js.native
}
