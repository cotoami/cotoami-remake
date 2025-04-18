package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, LocalNode, Node}
import cotoami.repository.Nodes
import cotoami.backend.{ErrorJson, LocalNodeBackend}
import cotoami.subparts.Modal

object FieldImageMaxSize {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      originalValue: String = "",
      input: String = "",
      editing: Boolean = false,
      saving: Boolean = false
  ) {
    def isLocal(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isSelf(implicit context: Context): Boolean =
      context.repo.nodes.isSelf(nodeId)

    def edit: Model = copy(editing = true)

    def cancelEditing(implicit context: Context): Model =
      copy(
        input = context.repo.nodes.selfSettings
          .flatMap(_.imageMaxSize)
          .map(_.toString())
          .getOrElse(""),
        editing = false
      )

    def changed: Boolean = originalValue != input

    def value: Either[Unit, Option[Int]] =
      if (input.isEmpty())
        Right(None)
      else
        input.toIntOption
          .map(size => Right(Some(size)))
          .getOrElse(Left(()))

    def readyToSave: Boolean = value.isRight && changed

    def save: (Model, Cmd[AppMsg]) =
      (
        copy(saving = true),
        value.map(setImageMaxSize).getOrElse(Cmd.none)
      )

    private def setImageMaxSize(size: Option[Int]): Cmd[AppMsg] =
      LocalNodeBackend.setImageMaxSize(size).map(Msg.Saved(_).into)

    def reset(local: LocalNode): Model = {
      val originalValue = local.imageMaxSize.map(_.toString()).getOrElse("")
      copy(
        originalValue = originalValue,
        input = originalValue,
        editing = false,
        saving = false
      )
    }
  }

  object Model {
    def apply(nodeId: Id[Node])(implicit context: Context): Model =
      context.repo.nodes.selfSettings
        .map(new Model(nodeId).reset(_))
        .getOrElse(new Model(nodeId))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into =
      ModalNodeProfile.Msg.FieldImageMaxSizeMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Edit extends Msg
    case object CancelEditing extends Msg
    case class Input(size: String) extends Msg
    case object Save extends Msg
    case class Saved(result: Either[ErrorJson, LocalNode]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Nodes, Cmd[AppMsg]) = {
    val default = (model, context.repo.nodes, Cmd.none)
    msg match {
      case Msg.Edit => default.copy(_1 = model.edit)

      case Msg.CancelEditing => default.copy(_1 = model.cancelEditing)

      case Msg.Input(size) => default.copy(_1 = model.copy(input = size))

      case Msg.Save =>
        model.save.pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Saved(Right(local)) =>
        default.copy(
          _1 = model.reset(local),
          _2 = context.repo.nodes.modify(_.selfSettings).setTo(Some(local))
        )

      case Msg.Saved(Left(e)) =>
        default.copy(_3 = cotoami.error("Couldn't save image max size.", e))
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Option.when(model.isSelf) {
      fieldImageMaxSize(model)
    }

  private def fieldImageMaxSize(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    fieldEditable(
      name = context.i18n.text.FieldImageMaxSize,
      classes = "image-max-size",
      edit = FieldEdit(
        onEditClick = _ => dispatch(Msg.Edit.into),
        onSaveClick = _ => dispatch(Msg.Save.into),
        onCancelClick = _ => dispatch(Msg.CancelEditing.into),
        editing = model.editing,
        readyToSave = model.readyToSave,
        saving = model.saving,
        error = None
      )
    )(
      input(
        `type` := "text",
        readOnly := !model.editing,
        placeholder := context.i18n.text.FieldImageMaxSize_placeholder,
        value := model.input,
        aria - "invalid" :=
          (if (model.changed) model.value.isLeft.toString() else ""),
        onChange := (e => dispatch(Msg.Input(e.target.value)))
      )
    )
}
