package cotoami.subparts.modals

import scala.util.chaining._

import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.Validation
import marubinotto.components.materialSymbol

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ClientNode, Id, Node}
import cotoami.repository.Nodes
import cotoami.backend.{
  ChildNodeInput,
  ClientAdded,
  ClientNodeBackend,
  ErrorJson
}
import cotoami.subparts.{field, fieldInput, Modal}
import cotoami.subparts.PartsNode

object ModalNewClient {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: String = "",
      nodeIdValidation: Validation.Result = Validation.Result.notYetValidated,
      childInput: ChildNodeInput = ChildNodeInput(),
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
    case object AsOwnerToggled extends Msg
    case object CanEditItosToggled extends Msg
    case object CanPostCotonomasToggled extends Msg
    case object Register extends Msg
    case class Registered(result: Either[ErrorJson, ClientAdded]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Nodes, Cmd[AppMsg]) = {
    val default = (model, context.repo.nodes, Cmd.none)
    msg match {
      case Msg.NodeIdInput(nodeId) =>
        model.copy(nodeId = nodeId)
          .validateNodeId(context.repo.nodes.operatedId)
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

      case Msg.AsOwnerToggled =>
        default.copy(_1 = model.modify(_.childInput.asOwner).using(!_))

      case Msg.CanEditItosToggled =>
        default.copy(_1 = model.modify(_.childInput.canEditItos).using(!_))

      case Msg.CanPostCotonomasToggled =>
        default.copy(_1 = model.modify(_.childInput.canPostCotonomas).using(!_))

      case Msg.Register =>
        default.copy(
          _1 = model.copy(registering = true),
          _3 = ClientNodeBackend.add(
            Id(model.nodeId),
            Some(model.childInput.toJson)
          ).map(Msg.Registered(_).into)
        )

      case Msg.Registered(Right(client)) =>
        default.copy(
          _1 = model.copy(
            registering = false,
            generatedPassword = Some(client.password)
          ),
          _2 = context.repo.nodes.put(client.node),
          _3 = Browser.send(ModalClients.Msg.FetchFirst.into)
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
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "new-client",
      closeButton = Some((classOf[Modal.NewClient], dispatch)),
      error = model.error
    )(
      context.i18n.text.ModalNewClient_title
    )(
      Option.when(model.registered) {
        section(className := "notice")(
          materialSymbol("check_circle", "completed"),
          context.i18n.text.ModalNewClient_registered
        )
      },
      form()(
        // Node ID
        fieldInput(
          name = "Node ID",
          classes = "node-id",
          inputPlaceholder = Some("00000000-0000-0000-0000-000000000000"),
          inputValue = model.nodeId,
          inputErrors = Option.when(!model.registered)(model.nodeIdValidation),
          readOnly = model.registered,
          onInput = (input => dispatch(Msg.NodeIdInput(input)))
        ),

        // Privileges
        field(
          name = context.i18n.text.ChildPrivileges,
          classes = "privileges"
        )(
          PartsNode.inputChildPrivileges(
            values = model.childInput,
            disabled = model.registered,
            onAsOwnerChange = (_ => dispatch(Msg.AsOwnerToggled)),
            onCanEditItosChange = (_ => dispatch(Msg.CanEditItosToggled)),
            onCanPostCotonomas = (_ => dispatch(Msg.CanPostCotonomasToggled))
          )
        ),

        // Generated password
        Option.when(model.registered) {
          fieldInput(
            name = "Generated password",
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
