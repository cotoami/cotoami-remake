package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.subparts.Modal

object FieldsSelf {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      originalImageMaxSize: String,
      imageMaxSizeInput: String,
      editingImageMaxSize: Boolean = false,
      savingImageMaxSize: Boolean = false
  ) {
    def isLocal(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isSelf(implicit context: Context): Boolean =
      context.repo.nodes.isSelf(nodeId)

    def editImageMaxSize: Model = copy(editingImageMaxSize = true)

    def cancelEditingImageMaxSize(implicit context: Context): Model =
      copy(
        imageMaxSizeInput = context.repo.nodes.selfSettings
          .flatMap(_.imageMaxSize)
          .map(_.toString())
          .getOrElse(""),
        editingImageMaxSize = false
      )

    def changed: Boolean = originalImageMaxSize != imageMaxSizeInput

    def imageMaxSize: Either[Unit, Option[Int]] =
      if (imageMaxSizeInput.isEmpty())
        Right(None)
      else
        imageMaxSizeInput.toIntOption
          .map(size => Right(Some(size)))
          .getOrElse(Left(()))

    def readyToSaveImageMaxSize(implicit context: Context): Boolean =
      imageMaxSize match {
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
        originalImageMaxSize = originalValue,
        imageMaxSizeInput = originalValue
      )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into =
      ModalNodeProfile.Msg.FieldsSelfMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object EditImageMaxSize extends Msg
    case object CancelEditingImageMaxSize extends Msg
    case class ImageMaxSizeInput(size: String) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.EditImageMaxSize =>
        (model.editImageMaxSize, Cmd.none)

      case Msg.CancelEditingImageMaxSize =>
        (model.cancelEditingImageMaxSize, Cmd.none)

      case Msg.ImageMaxSizeInput(size) =>
        (model.copy(imageMaxSizeInput = size), Cmd.none)
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
        onEditClick = _ => dispatch(Msg.EditImageMaxSize.into),
        // onSaveClick = _ => dispatch(Msg.Save.into),
        onCancelClick = _ => dispatch(Msg.CancelEditingImageMaxSize.into),
        editing = model.editingImageMaxSize,
        readyToSave = model.readyToSaveImageMaxSize,
        saving = model.savingImageMaxSize,
        error = None
      )
    )(
      input(
        `type` := "text",
        readOnly := !model.editingImageMaxSize,
        placeholder := context.i18n.text.FieldsSelf_imageMaxSize_placeholder,
        value := model.imageMaxSizeInput,
        aria - "invalid" :=
          (if (model.changed)
             model.imageMaxSize.isLeft.toString()
           else
             ""),
        onChange := (e => dispatch(Msg.ImageMaxSizeInput(e.target.value)))
      )
    )

}
