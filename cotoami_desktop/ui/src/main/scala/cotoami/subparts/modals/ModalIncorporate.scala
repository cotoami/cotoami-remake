package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.Validation

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Node, Server, ServerNode}
import cotoami.backend.{ClientNodeSession, ErrorJson, ServerBackend}
import cotoami.repository.Nodes
import cotoami.subparts.{Modal, PartsCoto, PartsNode}

object ModalIncorporate {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      error: Option[String] = None,

      // form
      nodeUrlInput: String = "",
      passwordInput: String = "",

      // connect
      connecting: Boolean = false,
      nodeSession: Option[ClientNodeSession] = None,

      // incorporate
      incorporating: Boolean = false
  ) {
    def nodeUrl: String = nodeUrlInput.trim()

    def password: Option[String] =
      Option.when(!passwordInput.isEmpty())(passwordInput)

    def validateNodeUrl: Validation.Result =
      if (nodeUrl.isBlank())
        Validation.Result.notYetValidated
      else
        Validation.Result(ServerNode.validateUrl(nodeUrl))

    def readyToConnect: Boolean = !connecting && validateNodeUrl.validated

    def connect: (Model, Cmd.One[AppMsg]) =
      (
        copy(connecting = true, error = None),
        ClientNodeSession.logIntoServer(nodeUrl, password)
          .map(Msg.Connected(_).into)
      )

    def readyToIncorporate: Boolean = !connecting && !incorporating

    def incorporate: (Model, Cmd.One[AppMsg]) =
      (
        copy(incorporating = true, error = None),
        ServerBackend.addServer(nodeUrl, password)
          .map(Msg.Incorporated(_).into)
      )

    def cancel: Model =
      copy(
        connecting = false,
        error = None,
        nodeSession = None
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.IncorporateMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class NodeUrlInput(url: String) extends Msg
    case class PasswordInput(password: String) extends Msg
    case object Connect extends Msg
    case class Connected(result: Either[ErrorJson, ClientNodeSession])
        extends Msg
    case object Cancel extends Msg
    case object Incorporate extends Msg
    case class Incorporated(result: Either[ErrorJson, Server]) extends Msg
  }

  def update(
      msg: Msg,
      model: Model
  )(implicit
      context: Context
  ): (Model, Nodes, Cmd[AppMsg]) = {
    val nodes = context.repo.nodes
    val default = (model, nodes, Cmd.none)
    msg match {
      case Msg.NodeUrlInput(url) =>
        default.copy(_1 = model.copy(nodeUrlInput = url))

      case Msg.PasswordInput(password) =>
        default.copy(_1 = model.copy(passwordInput = password))

      case Msg.Connect =>
        model.connect.pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Connected(Right(session)) => {
        if (nodes.servers.contains(session.server.id))
          default.copy(
            _1 = model.copy(
              connecting = false,
              error = Some("This node has already been registered as a server.")
            )
          )
        else
          default.copy(
            _1 = model.copy(
              connecting = false,
              error = None,
              nodeSession = Some(session)
            ),
            _2 = nodes.put(session.server)
          )
      }

      case Msg.Connected(Left(e)) =>
        default.copy(
          _1 = model.copy(
            connecting = false,
            error = Some(e.default_message)
          )
        )

      case Msg.Cancel => default.copy(_1 = model.cancel)

      case Msg.Incorporate =>
        model.incorporate.pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Incorporated(Right(server)) =>
        default.copy(
          _1 = model.copy(incorporating = false, error = None),
          _2 = nodes.addServer(server),
          _3 = Modal.close(classOf[Modal.Incorporate])
        )

      case Msg.Incorporated(Left(e)) =>
        default.copy(
          _1 = model.copy(
            incorporating = false,
            error = Some(e.default_message)
          ),
          _3 = cotoami.error("Node incorporating error.", e)
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
      dialogClasses = "incorporate",
      closeButton = Some((classOf[Modal.Incorporate], dispatch)),
      error = model.error
    )(
      Modal.spanTitleIcon(Node.IconName),
      context.i18n.text.ModalIncorporate_title
    )(
      model.nodeSession
        .map(sectionIncorporate(model, _))
        .getOrElse(sectionConnect(model))
    )

  private def sectionConnect(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "connect")(
      form()(
        // Node URL
        fieldInput(
          name = "Node URL",
          classes = "field-node-url",
          inputPlaceholder = Some("https://example.com"),
          inputValue = model.nodeUrlInput,
          inputErrors = Some(model.validateNodeUrl),
          onInput = (input => dispatch(Msg.NodeUrlInput(input)))
        ),

        // Password
        fieldInput(
          name = "Password",
          inputType = "password",
          inputValue = model.passwordInput,
          onInput = (input => dispatch(Msg.PasswordInput(input)))
        ),

        // Preview
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.readyToConnect,
            aria - "busy" := model.connecting.toString(),
            onClick := (e => {
              e.preventDefault()
              dispatch(Msg.Connect)
            })
          )("Preview")
        )
      )
    )

  private def sectionIncorporate(
      model: Model,
      nodeSession: ClientNodeSession
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "incorporate")(
      // Node preview
      section(className := "node-preview")(
        section(className := "node")(PartsNode.spanNode(nodeSession.server)),
        sectionChildPrivileges(nodeSession),
        nodeSession.serverRoot.map { case (_, coto) =>
          PartsCoto.sectionCotonomaContent(coto)
            .map(section(className := "node-description")(_))
        }
      ),

      // Incorporate button
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (e => dispatch(Msg.Cancel))
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToIncorporate,
          aria - "busy" := model.incorporating.toString(),
          onClick := (e => dispatch(Msg.Incorporate))
        )("Incorporate")
      )
    )

  private def sectionChildPrivileges(
      nodeSession: ClientNodeSession
  )(implicit context: Context): ReactElement =
    section(className := "child-privileges")(
      "Your privileges: ",
      span(className := "privileges")(
        PartsNode.childPrivileges(nodeSession.childPrivileges).mkString(", ")
      )
    )
}
