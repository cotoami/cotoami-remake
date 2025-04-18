package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.backend.{DatabaseInfo, ErrorJson}
import cotoami.subparts.{buttonEdit, field, Modal}

object FieldsSelf {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      imageMaxSizeInput: String = "",
      resettingPassword: Boolean = false,
      resettingPasswordError: Option[String] = None
  ) {
    def isLocal(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isSelf(implicit context: Context): Boolean =
      context.repo.nodes.isSelf(nodeId)
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
    case class ImageMaxSizeInput(size: String) extends Msg
    case object ResetOwnerPassword extends Msg
    case class OwnerPasswordReset(result: Either[ErrorJson, String]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
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
    field(
      name = context.i18n.text.FieldsSelf_imageMaxSize,
      classes = "image-max-size"
    )(
      input(
        `type` := "text",
        readOnly := true,
        placeholder := context.i18n.text.FieldsSelf_imageMaxSize_placeholder,
        value :=
          context.repo.nodes.localSettings
            .flatMap(_.imageMaxSize)
            .map(_.toString()),
        onChange := (e => dispatch(Msg.ImageMaxSizeInput(e.target.value)))
      ),
      div(className := "edit")(
        buttonEdit(_ => ())
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
