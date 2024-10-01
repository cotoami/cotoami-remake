package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{ActiveClient, ClientNode, Id, Node, Page}

@js.native
trait ClientNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val session_expires_at: Option[String] = js.native
  val disabled: Boolean = js.native
}

object ClientNodeJson {
  def fetchRecent(
      pageIndex: Double,
      pageSize: Option[Double] = None
  ): Cmd.One[Either[ErrorJson, PageJson[ClientNodeJson]]] =
    Commands.send(Commands.RecentClients(pageIndex, pageSize))

  def add(
      nodeId: Id[Node],
      canEditLinks: Boolean,
      asOowner: Boolean
  ): Cmd.One[Either[ErrorJson, ClientAddedJson]] =
    Commands.send(Commands.AddClient(nodeId, canEditLinks, asOowner))
}

object ClientNodeBackend {
  def toModel(json: ClientNodeJson): ClientNode =
    ClientNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      sessionExpiresAtUtcIso = json.session_expires_at,
      disabled = json.disabled
    )

  def fetchRecent(
      pageIndex: Double,
      pageSize: Option[Double] = None
  ): Cmd.One[Either[ErrorJson, Page[ClientNode]]] =
    ClientNodeJson.fetchRecent(pageIndex, pageSize)
      .map(_.map(PageBackend.toModel(_, toModel(_))))

  def add(
      nodeId: Id[Node],
      canEditLinks: Boolean,
      asOowner: Boolean
  ): Cmd.One[Either[ErrorJson, ClientAdded]] =
    ClientNodeJson.add(nodeId, canEditLinks, asOowner)
      .map(_.map(ClientAdded(_)))
}

@js.native
trait ClientAddedJson extends js.Object {
  val password: String = js.native
  val client: ClientNodeJson = js.native
  val node: NodeJson = js.native
}

case class ClientAdded(json: ClientAddedJson) {
  def password: String = json.password
  def client: ClientNode = ClientNodeBackend.toModel(json.client)
  def node: Node = NodeBackend.toModel(json.node)
}

@js.native
trait ActiveClientJson extends js.Object {
  val node_id: String = js.native
  val remote_addr: String = js.native
}

object ActiveClientBackend {
  def toModel(json: ActiveClientJson): ActiveClient =
    ActiveClient(
      nodeId = Id(json.node_id),
      remoteAddr = json.remote_addr
    )
}
