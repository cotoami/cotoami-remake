package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalConfirm {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      message: ReactElement,
      msgOnConfirm: AppMsg
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ConfirmMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Confirm extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Confirm =>
        (
          model,
          Cmd.Batch(
            Browser.send(model.msgOnConfirm),
            Modal.close(classOf[Modal.Confirm])
          )
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.Confirm]
    Modal.view(
      dialogClasses = "confirm",
      closeButton = Some((modalType, dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("check_circle"),
        context.i18n.text.ModalConfirm_title
      )
    )(
      section(className := "confirmation-message")(model.message),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
        )(context.i18n.text.Cancel),
        button(
          `type` := "button",
          onClick := (_ => dispatch(Msg.Confirm))
        )(context.i18n.text.OK)
      )
    )
  }
}
