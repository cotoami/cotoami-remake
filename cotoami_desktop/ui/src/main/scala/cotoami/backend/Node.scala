package cotoami.backend

import scala.scalajs.js

import fui.Cmd

@js.native
trait NodeJson extends js.Object {
  val uuid: String = js.native
  val icon: String = js.native // Base64 encoded image binary
  val name: String = js.native
  val root_cotonoma_id: Nullable[String] = js.native
  val version: Int = js.native
  val created_at: String = js.native
}

object NodeJson {
  def setLocalNodeIcon(icon: String): Cmd[Either[ErrorJson, NodeJson]] =
    Commands.send(Commands.SetLocalNodeIcon(icon))
}

@js.native
trait DatabaseRoleJson extends js.Object {
  val Parent: js.UndefOr[ParentNodeJson] = js.native
  val Child: js.UndefOr[ChildNodeJson] = js.native
}
