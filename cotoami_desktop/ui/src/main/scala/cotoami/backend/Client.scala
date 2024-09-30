package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{Id, Node}

@js.native
trait ClientNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val session_expires_at: Option[String] = js.native
  val disabled: Boolean = js.native
}

object ClientNodeJson {
  def fetchRecent(
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, PaginatedJson[ClientNodeJson]]] =
    Commands.send(Commands.RecentClients(pageIndex))

  def add(
      nodeId: Id[Node],
      canEditLinks: Boolean,
      asOowner: Boolean
  ): Cmd.One[Either[ErrorJson, PaginatedJson[ClientAddedJson]]] =
    Commands.send(Commands.AddClient(nodeId, canEditLinks, asOowner))
}

@js.native
trait ClientAddedJson extends js.Object {
  val password: String = js.native
  val client: ClientNodeJson = js.native
  val node: NodeJson = js.native
}
