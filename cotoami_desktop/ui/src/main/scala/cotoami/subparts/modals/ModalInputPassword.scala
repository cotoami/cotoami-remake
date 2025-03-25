package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalInputPassword {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      passwordInput: String = ""
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.InputPasswordMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.InputPassword]
    Modal.view(
      dialogClasses = "confirm",
      closeButton = Some((modalType, dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("key"),
        "Input Owner Password"
      )
    )(
      section(className := "input-password")(
        input(
          `type` := "password",
          value := model.passwordInput
        )
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
        )("Cancel"),
        button(
          `type` := "button"
        )("OK")
      )
    )
  }
}
