package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Id, Node}
import cotoami.backend.{ChildNodeBackend, ErrorJson}
import cotoami.subparts.{buttonEdit, field, Modal, PartsNode}

object SectionAsChild {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      loading: Boolean = false,
      child: Option[ChildNode] = None,
      editing: Boolean = false,
      asOwner: Boolean = false,
      canEditItos: Boolean = false
  ) {
    def isLocalNode(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def setChild(child: ChildNode): Model =
      copy(
        child = Some(child),
        asOwner = child.asOwner,
        canEditItos = child.canEditItos
      )
  }

  object Model {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (!context.repo.nodes.isOperating(nodeId))
        (
          Model(nodeId, loading = true),
          ChildNodeBackend.fetch(nodeId)
            .map(Msg.ChildNodeFetched(_).into)
        )
      else
        (Model(nodeId), Cmd.none)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into =
      ModalNodeProfile.Msg.SectionAsChildMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class ChildNodeFetched(result: Either[ErrorJson, ChildNode])
        extends Msg
    case object Edit extends Msg
    case object CancelEditing extends Msg
    case object AsOwnerToggled extends Msg
    case object CanEditItosToggled extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.ChildNodeFetched(Right(child)) =>
        (model.copy(loading = false).setChild(child), Cmd.none)

      case Msg.ChildNodeFetched(Left(_)) =>
        (model.copy(loading = false), Cmd.none)

      case Msg.Edit =>
        (model.copy(editing = true), Cmd.none)

      case Msg.CancelEditing =>
        (
          model.child
            .map(child =>
              model.copy(
                asOwner = child.asOwner,
                canEditItos = child.canEditItos
              )
            )
            .getOrElse(model)
            .copy(editing = false),
          Cmd.none
        )

      case Msg.AsOwnerToggled =>
        (model.copy(asOwner = !model.asOwner), Cmd.none)

      case Msg.CanEditItosToggled =>
        (model.copy(canEditItos = !model.canEditItos), Cmd.none)
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    model.child.map { child =>
      section(className := "field-group as-child")(
        h2()(context.i18n.text.AsChild_title),
        fieldChildPrivileges(model)
      )
    }.getOrElse(
      Option.when(model.loading) {
        section(
          className := "field-group as-child",
          aria - "busy" := model.loading.toString()
        )()
      }
    )

  private def fieldChildPrivileges(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.ChildPrivileges,
      classes = "privileges"
    )(
      PartsNode.inputChildPrivileges(
        asOwner = model.asOwner,
        canEditItos = model.canEditItos,
        disabled = !model.editing,
        onAsOwnerChange = (_ => dispatch(Msg.AsOwnerToggled)),
        onCanEditItosChange = (_ => dispatch(Msg.CanEditItosToggled))
      ),
      Option.when(!model.isLocalNode) {
        div(className := "edit")(
          if (model.editing)
            Fragment(
              toolButton(
                classes = "save",
                symbol = "check",
                tip = Some(context.i18n.text.Save),
                onClick = _ => ()
              ),
              toolButton(
                classes = "cancel",
                symbol = "close",
                tip = Some(context.i18n.text.Cancel),
                onClick = _ => dispatch(Msg.CancelEditing.into)
              )
            )
          else
            buttonEdit(_ => dispatch(Msg.Edit.into))
        )
      }
    )
}
