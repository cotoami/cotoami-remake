package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import marubinotto.facade.Nullable
import cotoami.models.{Coto, Cotonoma, Id, Node}

case class NodeDetails(
    json: NodeDetailsJson,
    node: Node,
    root: Option[(Cotonoma, Coto)]
)

object NodeDetails {
  def decode(json: NodeDetailsJson): IO[NodeDetails] =
    for {
      node <- NodeBackend.toModel(json.node)
      root <- Nullable.toOption(json.root).traverse(CotonomaBackend.decodePair)
    } yield NodeDetails(json, node, root)

  def fetch(id: Id[Node]): IO[Either[ErrorJson, NodeDetails]] =
    NodeDetailsJson.fetch(id).flatMap(_.traverse(NodeDetails.decode))
}

@js.native
trait NodeDetailsJson extends js.Object {
  val node: NodeJson = js.native
  val root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] = js.native
}

object NodeDetailsJson {
  def fetch(id: Id[Node]): IO[Either[ErrorJson, NodeDetailsJson]] =
    Commands.send(Commands.NodeDetails(id))
}
