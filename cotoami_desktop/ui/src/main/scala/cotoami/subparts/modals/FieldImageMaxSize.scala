package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.subparts.Modal

object FieldImageMaxSize {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      originalValue: String,
      input: String,
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

    def readyToSave(implicit context: Context): Boolean =
      value match {
        case Right(size) =>
          // Changed from the original?
          size != context.repo.nodes.selfSettings.flatMap(_.imageMaxSize)
        case Left(_) => false
      }
  }

  object Model {
    def apply(nodeId: Id[Node])(implicit context: Context): Model = {
      val originalValue = context.repo.nodes.selfSettings
        .flatMap(_.imageMaxSize)
        .map(_.toString())
        .getOrElse("")
      Model(
        nodeId,
        originalValue = originalValue,
        input = originalValue
      )
    }
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
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Edit =>
        (model.edit, Cmd.none)

      case Msg.CancelEditing =>
        (model.cancelEditing, Cmd.none)

      case Msg.Input(size) =>
        (model.copy(input = size), Cmd.none)
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
      name = context.i18n.text.FieldsSelf_imageMaxSize,
      classes = "image-max-size",
      edit = FieldEdit(
        onEditClick = _ => dispatch(Msg.Edit.into),
        // onSaveClick = _ => dispatch(Msg.Save.into),
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
        placeholder := context.i18n.text.FieldsSelf_imageMaxSize_placeholder,
        value := model.input,
        aria - "invalid" :=
          (if (model.changed) model.value.isLeft.toString() else ""),
        onChange := (e => dispatch(Msg.Input(e.target.value)))
      )
    )
}
