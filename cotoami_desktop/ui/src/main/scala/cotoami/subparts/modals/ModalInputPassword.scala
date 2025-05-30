package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Node
import cotoami.subparts.{Modal, PartsNode}

object ModalInputPassword {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      msgOnSubmit: String => AppMsg,
      title: String,
      message: Option[String] = None,
      targetNode: Option[Node] = None,
      passwordInput: String = "",
      submitting: Boolean = false
  ) {
    def readyToSubmit: Boolean = !submitting && !passwordInput.isBlank()

    def createMsg: AppMsg = msgOnSubmit(passwordInput)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.InputPasswordMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class PasswordInput(password: String) extends Msg
    case object Submit extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.PasswordInput(password) =>
        (model.copy(passwordInput = password), Cmd.none)

      case Msg.Submit =>
        (
          model.copy(submitting = true),
          Cmd.Batch(
            Browser.send(model.createMsg),
            Modal.close(classOf[Modal.InputPassword])
          )
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.InputPassword]
    Modal.view(
      dialogClasses = "input-password",
      closeButton = Some((modalType, dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("key"),
        model.title
      )
    )(
      model.targetNode.map(node =>
        section(className := "target-node")(
          PartsNode.spanNode(node)
        )
      ),
      model.message.map(section(className := "message")(_)),
      section(className := "input-password")(
        input(
          `type` := "password",
          value := model.passwordInput,
          onChange := (e => dispatch(Msg.PasswordInput(e.target.value)))
        )
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToSubmit,
          aria - "busy" := model.submitting.toString(),
          onClick := (_ => dispatch(Msg.Submit))
        )("OK")
      )
    )
  }
}
