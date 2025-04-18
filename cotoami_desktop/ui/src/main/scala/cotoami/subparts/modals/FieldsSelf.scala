package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.backend.{DatabaseInfo, ErrorJson}
import cotoami.subparts.Modal

object FieldsSelf {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],

      // Image max size
      originalImageMaxSize: String,
      imageMaxSizeInput: String,
      editingImageMaxSize: Boolean = false,
      savingImageMaxSize: Boolean = false,

      // Reset owner password
      resettingPassword: Boolean = false,
      resettingPasswordError: Option[String] = None
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
    case object ResetOwnerPassword extends Msg
    case class OwnerPasswordReset(result: Either[ErrorJson, String]) extends Msg
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

      case Msg.ResetOwnerPassword =>
        (
          model.copy(resettingPassword = true),
          DatabaseInfo.newOwnerPassword
            .map(Msg.OwnerPasswordReset(_).into)
        )

      case Msg.OwnerPasswordReset(Right(password)) =>
        (
          model.copy(resettingPassword = false),
          Modal.open(Modal.NewPassword.forOwner(password))
        )

      case Msg.OwnerPasswordReset(Left(e)) =>
        (
          model.copy(
            resettingPassword = false,
            resettingPasswordError = Some(e.default_message)
          ),
          Cmd.none
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Option.when(model.isSelf) {
      Fragment(
        fieldImageMaxSize(model),
        Option.when(model.isLocal) {
          fieldOwnerPassword(model)
        }
      )
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

  private def fieldOwnerPassword(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.FieldsSelf_ownerPassword,
      classes = "owner-password"
    )(
      button(
        `type` := "button",
        className := "reset-password contrast outline",
        disabled := model.resettingPassword,
        aria - "busy" := model.resettingPassword.toString(),
        onClick := (_ =>
          dispatch(
            Modal.Msg.OpenModal(
              Modal.Confirm(
                context.i18n.text.Owner_confirmResetPassword,
                Msg.ResetOwnerPassword
              )
            )
          )
        )
      )(context.i18n.text.Owner_resetPassword),
      model.resettingPasswordError.map(
        section(className := "error")(_)
      )
    )
}
