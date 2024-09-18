package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{Id, Node}

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

case class NodeDetails(json: NodeDetailsJson) {
  def node: Node = Node(this.json.node)
  def root: Option[(Cotonoma, Coto)] =
    Nullable.toOption(this.json.root).map(pair =>
      (Cotonoma(pair._1), Coto(pair._2))
    )
}

object NodeDetails {
  def fetch(id: Id[Node]): Cmd[Either[ErrorJson, NodeDetails]] =
    NodeDetailsJson.fetch(id).map(_.map(NodeDetails(_)))
}

@js.native
trait NodeDetailsJson extends js.Object {
  val node: NodeJson = js.native
  val root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] = js.native
}

object NodeDetailsJson {
  def fetch(id: Id[Node]): Cmd[Either[ErrorJson, NodeDetailsJson]] =
    Commands.send(Commands.NodeDetails(id))
}
