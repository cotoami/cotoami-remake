package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.backend.{DatabaseInfo, ErrorJson}
import cotoami.subparts.Modal

object FieldOwnerPassword {

  case class Model(
      nodeId: Id[Node],
      resetting: Boolean = false,
      error: Option[String] = None
  ) {
    def isLocal(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isSelf(implicit context: Context): Boolean =
      context.repo.nodes.isSelf(nodeId)
  }

  sealed trait Msg extends Into[AppMsg] {
    def into =
      ModalNodeProfile.Msg.FieldOwnerPasswordMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object ResetOwnerPassword extends Msg
    case class OwnerPasswordReset(result: Either[ErrorJson, String]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.ResetOwnerPassword =>
        (
          model.copy(resetting = true),
          DatabaseInfo.newOwnerPassword
            .map(Msg.OwnerPasswordReset(_).into)
        )

      case Msg.OwnerPasswordReset(Right(password)) =>
        (
          model.copy(resetting = false),
          Modal.open(Modal.NewPassword.forOwner(password))
        )

      case Msg.OwnerPasswordReset(Left(e)) =>
        (
          model.copy(
            resetting = false,
            error = Some(e.default_message)
          ),
          Cmd.none
        )
    }

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Option.when(model.isSelf && model.isLocal) {
      fieldOwnerPassword(model)
    }

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
        disabled := model.resetting,
        aria - "busy" := model.resetting.toString(),
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
      model.error.map(section(className := "error")(_))
    )
}
