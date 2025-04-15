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
  val can_post_cotonomas: Boolean = js.native
}

object ChildNodeJson {
  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, ChildNodeJson]] =
    Commands.send(Commands.ChildNode(id))

  def edit(
      id: Id[Node],
      values: ChildNodeInputJson
  ): Cmd.One[Either[ErrorJson, ChildNodeJson]] =
    Commands.send(Commands.EditChild(id, values))
}

object ChildNodeBackend {
  def toModel(json: ChildNodeJson): ChildNode =
    ChildNode(
      nodeId = Id(json.node_id),
      createdAtUtcIso = json.created_at,
      asOwner = json.as_owner,
      _canEditItos = json.can_edit_itos,
      _canPostCotonomas = json.can_post_cotonomas
    )

  def fetch(id: Id[Node]): Cmd.One[Either[ErrorJson, ChildNode]] =
    ChildNodeJson.fetch(id).map(_.map(toModel))

  def edit(
      id: Id[Node],
      values: ChildNodeInputJson
  ): Cmd.One[Either[ErrorJson, ChildNode]] =
    ChildNodeJson.edit(id, values).map(_.map(toModel))
}

trait ChildNodeInputJson extends js.Object {
  val as_owner: js.UndefOr[Boolean] = js.undefined
  val can_edit_itos: js.UndefOr[Boolean] = js.undefined
  val can_post_cotonomas: js.UndefOr[Boolean] = js.undefined
}

case class ChildNodeInput(
    asOwner: Boolean = false,
    canEditItos: Boolean = false,
    canPostCotonomas: Boolean = false
) {
  def toJson: ChildNodeInputJson =
    new ChildNodeInputJson {
      override val as_owner = asOwner
      override val can_edit_itos = canEditItos
      override val can_post_cotonomas = canPostCotonomas
    }
}

object ChildNodeInput {
  def apply(child: ChildNode): ChildNodeInput =
    ChildNodeInput(
      asOwner = child.asOwner,
      canEditItos = child.canEditItos,
      canPostCotonomas = child.canPostCotonomas
    )
}
