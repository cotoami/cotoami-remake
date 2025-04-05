package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import marubinotto.facade.Nullable
import cotoami.models.{Coto, Cotonoma, Id, Node}

case class NodeDetails(json: NodeDetailsJson) {
  def node: Node = NodeBackend.toModel(json.node)
  def root: Option[(Cotonoma, Coto)] =
    Nullable.toOption(json.root).map(pair =>
      (CotonomaBackend.toModel(pair._1), CotoBackend.toModel(pair._2))
    )
}

object NodeDetails {
  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, NodeDetails]] =
    NodeDetailsJson.fetch(id).map(_.map(NodeDetails(_)))
}

@js.native
trait NodeDetailsJson extends js.Object {
  val node: NodeJson = js.native
  val root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] = js.native
}

object NodeDetailsJson {
  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, NodeDetailsJson]] =
    Commands.send(Commands.NodeDetails(id))
}
