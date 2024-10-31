package cotoami.subparts.modals

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalConfirm {

  case class Model(
      message: String,
      msgOnConfirm: AppMsg
  )

  def apply(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.Confirm]
    Modal.view(
      dialogClasses = "confirm",
      closeButton = Some((modalType, dispatch))
    )(
      "Confirmation"
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
          onClick := (_ => dispatch(model.msgOnConfirm))
        )("OK")
      )
    )
  }
}
