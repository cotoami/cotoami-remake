package cotoami.subparts.modals

import scala.util.chaining._

import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{ClientNode, Id, Node}
import cotoami.repository.Nodes
import cotoami.backend.{ClientAdded, ClientNodeBackend, ErrorJson}
import cotoami.components.materialSymbol
import cotoami.subparts.{labeledField, labeledInputField, Modal}

object ModalNewClient {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: String = "",
      nodeIdValidation: Validation.Result = Validation.Result.notYetValidated,
      canEditItos: Boolean = false,
      asOwner: Boolean = false,
      error: Option[String] = None,
      registering: Boolean = false,
      generatedPassword: Option[String] = None
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

    def registered: Boolean = generatedPassword.isDefined
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NewClientMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class NodeIdInput(nodeId: String) extends Msg
    case class ClientNodeFetched(
        nodeId: String,
        result: Either[ErrorJson, ClientNode]
    ) extends Msg
    object CanEditItosToggled extends Msg
    object AsOwnerToggled extends Msg
    object Register extends Msg
    case class Registered(result: Either[ErrorJson, ClientAdded]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Nodes, Cmd[AppMsg]) = {
    val default = (model, context.repo.nodes, Cmd.none)
    msg match {
      case Msg.NodeIdInput(nodeId) =>
        model.copy(nodeId = nodeId)
          .validateNodeId(context.repo.nodes.operatingId)
          .pipe { case (model, cmd) =>
            default.copy(_1 = model, _3 = cmd)
          }

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
          default.copy(_3 =
            cotoami.error("Couldn't fetch the client node.", error)
          )

      case Msg.CanEditItosToggled =>
        default.copy(_1 = model.modify(_.canEditItos).using(!_))

      case Msg.AsOwnerToggled =>
        default.copy(_1 = model.modify(_.asOwner).using(!_))

      case Msg.Register =>
        default.copy(
          _1 = model.copy(registering = true),
          _3 = ClientNodeBackend.add(
            Id(model.nodeId),
            model.canEditItos,
            model.asOwner
          ).map(Msg.Registered(_).into)
        )

      case Msg.Registered(Right(client)) =>
        default.copy(
          _1 = model.copy(
            registering = false,
            generatedPassword = Some(client.password)
          ),
          _2 = context.repo.nodes.put(client.node),
          _3 = ModalClients.fetchClients(0)
        )

      case Msg.Registered(Left(e)) =>
        default.copy(
          _1 = model.copy(registering = false),
          _3 = cotoami.error("Couldn't register the node as a client.", e)
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "new-client",
      closeButton = Some((classOf[Modal.NewClient], dispatch)),
      error = model.error
    )(
      "New Client"
    )(
      Option.when(model.registered) {
        section(className := "message")(
          materialSymbol("check_circle", "completed"),
          "The child node below has been registered."
        )
      },
      form()(
        // Node ID
        labeledInputField(
          classes = "node-id",
          label = "Node ID",
          inputId = "node-id",
          inputType = "text",
          inputPlaceholder = Some("00000000-0000-0000-0000-000000000000"),
          inputValue = model.nodeId,
          inputErrors = Option.when(!model.registered)(model.nodeIdValidation),
          readOnly = model.registered,
          onInput = (input => dispatch(Msg.NodeIdInput(input)))
        ),

        // Privileges
        labeledField(
          classes = "privileges",
          label = "Privileges",
          labelFor = None
        )(
          label(htmlFor := "can-edit-itos")(
            input(
              `type` := "checkbox",
              id := "can-edit-itos",
              checked := model.canEditItos,
              disabled := model.registered,
              onChange := (_ => dispatch(Msg.CanEditItosToggled))
            ),
            "Permit to create itos (connect/disconnect)"
          ),
          label(htmlFor := "as-owner")(
            input(
              `type` := "checkbox",
              id := "as-owner",
              checked := model.asOwner,
              disabled := model.registered,
              onChange := (_ => dispatch(Msg.AsOwnerToggled))
            ),
            "As an owner"
          )
        ),

        // Generated password
        Option.when(model.registered) {
          labeledInputField(
            label = "Generated password",
            inputId = "password",
            inputType = "text",
            inputValue = model.generatedPassword.getOrElse(""),
            readOnly = true
          )
        },

        // Register
        div(className := "buttons")(
          if (model.registered)
            button(
              onClick := (e => {
                e.preventDefault()
                dispatch(Modal.Msg.CloseModal(classOf[Modal.NewClient]))
              })
            )("OK")
          else
            button(
              `type` := "submit",
              disabled := !model.readyToRegister,
              aria - "busy" := model.registering.toString(),
              onClick := (e => {
                e.preventDefault()
                dispatch(Msg.Register)
              })
            )("Register")
        )
      )
    )
}
