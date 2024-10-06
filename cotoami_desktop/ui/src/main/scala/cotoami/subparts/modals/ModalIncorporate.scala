package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.models.{Server, ServerNode}
import cotoami.backend.{ClientNodeSession, ErrorJson, ServerBackend}
import cotoami.repositories.Nodes
import cotoami.subparts.{
  buttonHelp,
  labeledInputField,
  sectionHelp,
  spanNode,
  Modal,
  ViewCoto
}

object ModalIncorporate {

  case class Model(
      helpIntro: Boolean = false,

      // connect
      helpConnect: Boolean = false,
      nodeUrl: String = "",
      password: String = "",
      connecting: Boolean = false,
      connectingError: Option[String] = None,
      nodeSession: Option[ClientNodeSession] = None,

      // incorporate
      incorporating: Boolean = false,
      incorporatingError: Option[String] = None
  ) {
    def validateNodeUrl: Validation.Result =
      if (nodeUrl.isBlank())
        Validation.Result.notYetValidated
      else
        Validation.Result(ServerNode.validateUrl(nodeUrl))

    def readyToConnect: Boolean = !connecting && validateNodeUrl.validated

    def readyToIncorporate: Boolean = !connecting && !incorporating
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.IncorporateMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class HelpIntro(display: Boolean) extends Msg
    case class HelpConnect(display: Boolean) extends Msg
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
    val nodes = context.domain.nodes
    val default = (model, nodes, Cmd.none)
    msg match {
      case Msg.HelpIntro(display) =>
        default.copy(_1 = model.copy(helpIntro = display))

      case Msg.HelpConnect(display) =>
        default.copy(_1 = model.copy(helpConnect = display))

      case Msg.NodeUrlInput(url) =>
        default.copy(_1 = model.copy(nodeUrl = url))

      case Msg.PasswordInput(password) =>
        default.copy(_1 = model.copy(password = password))

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

      case Msg.NodeIncorporated(Right(server)) => {
        println(s"NodeIncorporated: ${server}")
        default.copy(
          _1 = model.copy(incorporating = false, incorporatingError = None),
          _2 = nodes.addServer(server),
          _3 = Modal.close(classOf[Modal.Incorporate])
        )
      }

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
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "connect")(
      h2()(
        "Connect",
        buttonHelp(
          model.helpConnect,
          () => dispatch(Msg.HelpConnect(true))
        )
      ),
      model.connectingError.map(e => section(className := "error")(e)),
      form()(
        sectionHelp(
          model.helpConnect,
          () => dispatch(Msg.HelpConnect(false)),
          context.i18n.help.ModalIncorporate_connect(
            context.domain.nodes.operatingId.map(_.uuid).getOrElse("")
          )
        ),

        // Node URL
        labeledInputField(
          label = "Node URL",
          inputId = "node-url",
          inputType = "text",
          inputPlaceholder = Some("https://example.com"),
          inputValue = model.nodeUrl,
          inputErrors = model.validateNodeUrl,
          onInput = (input => dispatch(Msg.NodeUrlInput(input)))
        ),

        // Password
        labeledInputField(
          label = "Password",
          inputId = "password",
          inputType = "password",
          inputValue = model.password,
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
      h2()("Node"),
      model.incorporatingError.map(e => section(className := "error")(e)),

      // Node preview
      section(className := "node-preview")(
        section(className := "node")(spanNode(nodeSession.server)),
        nodeSession.asChild.map(child =>
          section(className := "child-privileges")(
            "Your privileges to this node: ",
            span(className := "privileges")(
              if (child.asOwner)
                "owner"
              else if (child.canEditLinks)
                "post, edit links"
              else
                "post"
            )
          )
        ),
        nodeSession.serverRoot.map { case (_, coto) =>
          ViewCoto.sectionNodeDescription(coto)
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
}
