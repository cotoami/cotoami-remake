package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.models.{Id, Node}

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
