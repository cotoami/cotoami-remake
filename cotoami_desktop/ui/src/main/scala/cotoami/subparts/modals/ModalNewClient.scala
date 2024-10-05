package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.subparts.{labeledInputField, Modal}

object ModalNewClient {

  case class Model(
      nodeId: String = "",
      canEditLinks: Boolean = false,
      asOwner: Boolean = false,
      error: Option[String] = None
  ) {
    def validateNodeId: Validation.Result = {
      val fieldName = "node ID"
      if (nodeId.isBlank())
        Validation.Result.notYetValidated
      else
        Validation.Result(
          Seq(
            Validation.nonBlank(fieldName, nodeId),
            Validation.uuid(fieldName, nodeId)
          ).flatten
        )
    }

  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NewClientMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class NodeIdInput(nodeId: String) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) = {
    val default = (model, Cmd.none)
    msg match {
      case Msg.NodeIdInput(nodeId) =>
        default.copy(_1 = model.copy(nodeId = nodeId))
    }
  }

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "new-client",
      closeButton = Some((classOf[Modal.NewClient], dispatch)),
      error = model.error
    )(
      "New client"
    )(
      form()(
        // Node ID
        labeledInputField(
          classes = "node-id",
          label = "Node ID",
          inputId = "node-id",
          inputType = "text",
          inputPlaceholder = Some("00000000-0000-0000-0000-000000000000"),
          inputValue = model.nodeId,
          inputErrors = model.validateNodeId,
          onInput = (input => dispatch(Msg.NodeIdInput(input)))
        )
      )
    )
}
