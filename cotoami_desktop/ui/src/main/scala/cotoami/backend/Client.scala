package cotoami.backend

import scala.scalajs.js

import fui.Cmd

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
  ): Cmd.One[Either[ErrorJson, PaginatedJson[ClientNodeJson]]] =
    Commands.send(Commands.RecentClients(pageIndex, pageSize))
}
