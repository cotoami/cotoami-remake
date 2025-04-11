package cotoami.backend

import scala.scalajs.js

import marubinotto.fui.Cmd
import cotoami.models.{ChildNode, Id, Node}

@js.native
trait ChildNodeJson extends js.Object {
  val node_id: String = js.native
  val created_at: String = js.native
  val as_owner: Boolean = js.native
  val can_edit_itos: Boolean = js.native
}

object ChildNodeJson {
  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, ChildNodeJson]] =
    Commands.send(Commands.ChildNode(id))

  def edit(
      id: Id[Node],
      asOwner: Boolean,
      canEditItos: Boolean
  ): Cmd.One[Either[ErrorJson, ChildNodeJson]] =
    Commands.send(Commands.EditChild(id, asOwner, canEditItos))
}

object ChildNodeBackend {
  def toModel(json: ChildNodeJson): ChildNode =
    ChildNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      asOwner = json.as_owner,
      canEditItos = json.can_edit_itos
    )

  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, ChildNode]] =
    ChildNodeJson.fetch(id).map(_.map(toModel))

  def edit(
      id: Id[Node],
      asOwner: Boolean,
      canEditItos: Boolean
  ): Cmd.One[Either[ErrorJson, ChildNode]] =
    ChildNodeJson.edit(id, asOwner, canEditItos)
      .map(_.map(toModel))
}
