package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Server, ServerNode}
import cotoami.backend.{ClientNodeSession, ErrorJson, ServerBackend}
import cotoami.repository.Nodes
import cotoami.subparts.{
  buttonHelp,
  labeledInputField,
  sectionHelp,
  spanNode,
  Modal,
  ViewCoto
}

object ModalIncorporate {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      helpIntro: Boolean = false,

      // form
      nodeUrlInput: String = "",
      passwordInput: String = "",

      // connect
      helpConnect: Boolean = false,
      connecting: Boolean = false,
      connectingError: Option[String] = None,
      nodeSession: Option[ClientNodeSession] = None,

      // incorporate
      incorporating: Boolean = false,
      incorporatingError: Option[String] = None
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

    def readyToIncorporate: Boolean = !connecting && !incorporating
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.IncorporateMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class HelpIntro(display: Boolean) extends Msg
    case class NodeUrlInput(url: String) extends Msg
    case class PasswordInput(password: String) extends Msg
    case object Connect extends Msg
    case class NodeConnected(result: Either[ErrorJson, ClientNodeSession])
        extends Msg
    case object Cancel extends Msg
    case object Incorporate extends Msg
    case class NodeIncorporated(result: Either[ErrorJson, Server]) extends Msg
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
      case Msg.HelpIntro(display) =>
        default.copy(_1 = model.copy(helpIntro = display))

      case Msg.NodeUrlInput(url) =>
        default.copy(_1 = model.copy(nodeUrlInput = url))

      case Msg.PasswordInput(password) =>
        default.copy(_1 = model.copy(passwordInput = password))

      case Msg.Connect =>
        default.copy(
          _1 = model.copy(connecting = true, connectingError = None),
          _3 = ClientNodeSession.logIntoServer(model.nodeUrl, model.password)
            .map(Msg.NodeConnected(_).into)
        )

      case Msg.NodeConnected(Right(session)) => {
        if (nodes.servers.contains(session.server.id))
          default.copy(
            _1 = model.copy(
              connecting = false,
              connectingError =
                Some("This node has already been registered as a server.")
            )
          )
        else
          default.copy(
            _1 = model.copy(
              connecting = false,
              connectingError = None,
              nodeSession = Some(session)
            ),
            _2 = nodes.put(session.server)
          )
      }

      case Msg.NodeConnected(Left(e)) =>
        default.copy(
          _1 = model.copy(
            connecting = false,
            connectingError = Some(e.default_message)
          ),
          _3 = cotoami.error("Node connecting error.", e)
        )

      case Msg.Cancel =>
        default.copy(
          _1 = model.copy(
            connecting = false,
            connectingError = None,
            incorporatingError = None,
            nodeSession = None
          )
        )

      case Msg.Incorporate =>
        default.copy(
          _1 = model.copy(incorporating = true, incorporatingError = None),
          _3 = ServerBackend.addServer(model.nodeUrl, model.password)
            .map(Msg.NodeIncorporated(_).into)
        )

      case Msg.NodeIncorporated(Right(server)) =>
        default.copy(
          _1 = model.copy(incorporating = false, incorporatingError = None),
          _2 = nodes.addServer(server),
          _3 = Modal.close(classOf[Modal.Incorporate])
        )

      case Msg.NodeIncorporated(Left(e)) =>
        default.copy(
          _1 = model.copy(
            incorporating = false,
            incorporatingError = Some(e.default_message)
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
      closeButton = Some((classOf[Modal.Incorporate], dispatch))
    )(
      "Incorporate Remote Database",
      buttonHelp(
        model.helpIntro,
        () => dispatch(Msg.HelpIntro(true))
      )
    )(
      sectionHelp(
        model.helpIntro,
        () => dispatch(Msg.HelpIntro(false)),
        context.i18n.help.ModalIncorporate_intro
      ),
      model.nodeSession
        .map(sectionIncorporate(model, _))
        .getOrElse(sectionConnect(model))
    )

  private def sectionConnect(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "connect")(
      model.connectingError.map(e => section(className := "error")(e)),
      form()(
        // Node URL
        labeledInputField(
          classes = "field-node-url",
          label = "Node URL",
          inputId = "node-url",
          inputType = "text",
          inputPlaceholder = Some("https://example.com"),
          inputValue = model.nodeUrlInput,
          inputErrors = Some(model.validateNodeUrl),
          onInput = (input => dispatch(Msg.NodeUrlInput(input)))
        ),

        // Password
        labeledInputField(
          label = "Password",
          inputId = "password",
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
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "incorporate")(
      model.incorporatingError.map(e => section(className := "error")(e)),

      // Node preview
      section(className := "node-preview")(
        section(className := "node")(spanNode(nodeSession.server)),
        sectionChildPrivileges(nodeSession),
        nodeSession.serverRoot.map { case (_, coto) =>
          ViewCoto.sectionCotonomaContent(coto)
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
  ): ReactElement =
    section(className := "child-privileges")(
      "Your privileges: ",
      span(className := "privileges")(
        nodeSession.asChild match {
          case Some(child) => {
            if (child.asOwner)
              "an owner"
            else if (child.canEditLinks)
              "post, edit links"
            else
              "post"
          }
          case None => "an anonymous read-only client"
        }
      )
    )
}
