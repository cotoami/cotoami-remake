package cotoami.subparts.modals

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Into, Msg => AppMsg}
import cotoami.models.Node
import cotoami.subparts.{Modal, PartsNode}

object ModalNewPassword {

  case class Model(
      title: String,
      message: String,
      principalNode: Option[Node],
      password: String
  )

  def apply(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.NewPassword]
    Modal.view(
      dialogClasses = "new-password",
      closeButton = Some((modalType, dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("key"),
        model.title
      )
    )(
      model.principalNode.map(node =>
        section(className := "principal-node")(
          PartsNode.spanNode(node)
        )
      ),
      section(className := "password")(
        input(
          `type` := "text",
          readOnly := true,
          value := model.password
        )
      ),
      section(className := "message")(model.message),
      div(className := "buttons")(
        button(
          `type` := "button",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
        )("OK")
      )
    )
  }
}
