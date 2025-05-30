package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable

import cotoami.models.{ActiveClient, ClientNode, Id, Node, Page}

@js.native
trait ClientNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val session_expires_at: Nullable[String] = js.native
  val disabled: Boolean = js.native
  val last_session_created_at: Nullable[String] = js.native
}

object ClientNodeJson {
  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, ClientNodeJson]] =
    Commands.send(Commands.ClientNode(id))

  def fetchRecent(
      pageIndex: Double,
      pageSize: Option[Double] = None
  ): Cmd.One[Either[ErrorJson, PageJson[ClientNodeJson]]] =
    Commands.send(Commands.RecentClients(pageIndex, pageSize))

  def add(
      nodeId: Id[Node],
      asChild: Option[ChildNodeInputJson]
  ): Cmd.One[Either[ErrorJson, ClientAddedJson]] =
    Commands.send(Commands.AddClient(nodeId, asChild))

  def edit(
      id: Id[Node],
      disabled: Option[Boolean]
  ): Cmd.One[Either[ErrorJson, ClientNodeJson]] =
    Commands.send(Commands.EditClient(id, disabled))
}

object ClientNodeBackend {
  def toModel(json: ClientNodeJson): ClientNode =
    ClientNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      sessionExpiresAtUtcIso = Nullable.toOption(json.session_expires_at),
      disabled = json.disabled,
      lastSessionCreatedAtUtcIso =
        Nullable.toOption(json.last_session_created_at)
    )

  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, ClientNode]] =
    ClientNodeJson.fetch(id).map(_.map(toModel))

  def fetchRecent(
      pageIndex: Double,
      pageSize: Option[Double] = None
  ): Cmd.One[Either[ErrorJson, Page[ClientNode]]] =
    ClientNodeJson.fetchRecent(pageIndex, pageSize)
      .map(_.map(PageBackend.toModel(_, toModel)))

  def add(
      nodeId: Id[Node],
      asChild: Option[ChildNodeInputJson]
  ): Cmd.One[Either[ErrorJson, ClientAdded]] =
    ClientNodeJson.add(nodeId, asChild)
      .map(_.map(ClientAdded(_)))

  def resetPassword(id: Id[Node]): Cmd.One[Either[ErrorJson, String]] =
    Commands.send(Commands.ResetClientPassword(id))

  def edit(
      id: Id[Node],
      disabled: Option[Boolean]
  ): Cmd.One[Either[ErrorJson, ClientNode]] =
    ClientNodeJson.edit(id, disabled).map(_.map(toModel))
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
