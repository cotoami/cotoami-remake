package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{ClientNode, Id, Node}
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.subparts.{labeledInputField, Modal}

object ModalNewClient {

  case class Model(
      nodeId: String = "",
      nodeIdValidation: Validation.Result = Validation.Result.notYetValidated,
      canEditLinks: Boolean = false,
      asOwner: Boolean = false,
      error: Option[String] = None,
      registering: Boolean = false
  ) {
    def validateNodeId(
        localNodeId: Option[Id[Node]]
    ): (Model, Cmd.One[AppMsg]) = {
      val (validation, cmd) =
        if (nodeId.isEmpty())
          (Validation.Result.notYetValidated, Cmd.none)
        else
          ClientNode.validateNodeId(nodeId, localNodeId) match {
            case Seq() =>
              (
                Validation.Result.notYetValidated,
                ClientNodeBackend.fetch(Id(nodeId))
                  .map(Msg.ClientNodeFetched(nodeId, _).into)
              )
            case errors => (Validation.Result(errors), Cmd.none)
          }
      (copy(nodeIdValidation = validation), cmd)
    }

    def readyToRegister: Boolean = !registering && nodeIdValidation.validated
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NewClientMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class NodeIdInput(nodeId: String) extends Msg
    case class ClientNodeFetched(
        nodeId: String,
        result: Either[ErrorJson, ClientNode]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) = {
    val default = (model, Cmd.none)
    msg match {
      case Msg.NodeIdInput(nodeId) =>
        model.copy(nodeId = nodeId)
          .validateNodeId(context.domain.nodes.operatingId)

      case Msg.ClientNodeFetched(nodeId, Right(client)) =>
        if (client.nodeId == Id(model.nodeId))
          default.copy(_1 =
            model.copy(nodeIdValidation =
              Validation.Error(
                "client-already-exists",
                "The node has already been registered as a client.",
                Map("id" -> model.nodeId)
              ).toResult
            )
          )
        else
          default

      case Msg.ClientNodeFetched(nodeId, Left(error)) =>
        if (nodeId == model.nodeId && error.code == "not-found")
          default.copy(_1 =
            model.copy(nodeIdValidation = Validation.Result.validated)
          )
        else
          default.copy(_2 =
            cotoami.error("Couldn't fetch the client node.", error)
          )
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
          inputErrors = model.nodeIdValidation,
          onInput = (input => dispatch(Msg.NodeIdInput(input)))
        ),

        // Register
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.readyToRegister,
            aria - "busy" := model.registering.toString(),
            onClick := (e => {
              e.preventDefault()
            })
          )("Register")
        )
      )
    )
}
