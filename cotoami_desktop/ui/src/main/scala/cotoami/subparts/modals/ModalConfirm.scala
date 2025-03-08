package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalConfirm {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      message: String,
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
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.Confirm]
    Modal.view(
      dialogClasses = "confirm",
      closeButton = Some((modalType, dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("check_circle"),
        "Confirmation"
      )
    )(
      section(className := "confirmation-message")(model.message),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
        )("Cancel"),
        button(
          `type` := "button",
          onClick := (_ => dispatch(Msg.Confirm))
        )("OK")
      )
    )
  }
}
