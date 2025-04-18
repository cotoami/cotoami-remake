package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Id, Node}
import cotoami.backend.{ChildNodeBackend, ChildNodeInput, ErrorJson}
import cotoami.subparts.{Modal, PartsNode}

object SectionAsChild {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      loading: Boolean = false,
      child: Option[ChildNode] = None,
      childInput: ChildNodeInput = ChildNodeInput(),
      editing: Boolean = false,
      saving: Boolean = false,
      savingError: Option[String] = None
  ) {
    def isLocal(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def setChild(child: ChildNode): Model =
      copy(
        child = Some(child),
        childInput = ChildNodeInput(child)
      )

    def save: (Model, Cmd[AppMsg]) =
      (
        copy(saving = true),
        ChildNodeBackend.edit(nodeId, childInput.toJson)
          .map(Msg.Saved(_).into)
      )
  }

  object Model {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (!context.repo.nodes.isSelf(nodeId))
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
    case object CanPostCotonomasToggled extends Msg
    case object Save extends Msg
    case class Saved(result: Either[ErrorJson, ChildNode]) extends Msg
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
            .map(child => model.copy(childInput = ChildNodeInput(child)))
            .getOrElse(model)
            .copy(editing = false),
          Cmd.none
        )

      case Msg.AsOwnerToggled =>
        (model.modify(_.childInput.asOwner).using(!_), Cmd.none)

      case Msg.CanEditItosToggled =>
        (model.modify(_.childInput.canEditItos).using(!_), Cmd.none)

      case Msg.CanPostCotonomasToggled =>
        (model.modify(_.childInput.canPostCotonomas).using(!_), Cmd.none)

      case Msg.Save => model.save

      case Msg.Saved(Right(child)) =>
        (
          model
            .copy(saving = false, editing = false)
            .setChild(child),
          Cmd.none
        )

      case Msg.Saved(Left(e)) =>
        (
          model.copy(
            saving = false,
            editing = false,
            savingError = Some(e.default_message)
          ),
          Cmd.none
        )
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
        values = model.childInput,
        disabled = !model.editing,
        onAsOwnerChange = (_ => dispatch(Msg.AsOwnerToggled)),
        onCanEditItosChange = (_ => dispatch(Msg.CanEditItosToggled)),
        onCanPostCotonomas = (_ => dispatch(Msg.CanPostCotonomasToggled))
      ),
      Option.when(!model.isLocal) {
        div(className := "edit")(
          if (model.saving)
            span(
              className := "processing",
              aria - "busy" := model.saving.toString()
            )()
          else if (model.editing)
            Fragment(
              toolButton(
                classes = "save",
                symbol = "check",
                tip = Some(context.i18n.text.Save),
                onClick = _ => dispatch(Msg.Save.into)
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
      },
      model.savingError.map(section(className := "error")(_))
    )
}
