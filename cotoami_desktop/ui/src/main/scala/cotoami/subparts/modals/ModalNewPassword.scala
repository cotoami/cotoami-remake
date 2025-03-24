package cotoami.subparts.modals

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalNewPassword {

  case class Model(
      password: String
  )

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.NewPassword]
    Modal.view(
      dialogClasses = "new-password",
      closeButton = Some((modalType, dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("key"),
        context.i18n.text.ModalNewPassword_title
      )
    )(
      section(className := "password")(
        input(
          `type` := "text",
          readOnly := true,
          value := model.password
        )
      ),
      section(className := "message")(
        context.i18n.text.ModalNewPassword_message
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
        )("OK")
      )
    )
  }
}
