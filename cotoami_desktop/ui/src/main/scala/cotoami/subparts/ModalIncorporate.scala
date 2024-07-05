package cotoami.subparts

import scala.scalajs.js
import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{log_error, Msg => AppMsg}
import cotoami.utils.Validation
import cotoami.backend.{
  ClientNodeSession,
  ClientNodeSessionJson,
  Commands,
  ErrorJson,
  Server,
  ServerJson,
  ServerNode
}
import cotoami.repositories.{Domain, Nodes}
import cotoami.models.Context

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
      if (this.nodeUrl.isBlank())
        Validation.Result.toBeValidated
      else
        Validation.Result(ServerNode.validateUrl(this.nodeUrl))

    def readyToConnect: Boolean = !this.connecting && validateNodeUrl.validated

    def readyToIncorporate: Boolean = !this.connecting && !this.incorporating
  }

  sealed trait Msg {
    def toApp: AppMsg =
      Modal.Msg.IncorporateMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.IncorporateMsg andThen AppMsg.ModalMsg

    case class HelpIntro(display: Boolean) extends Msg
    case class HelpConnect(display: Boolean) extends Msg
    case class NodeUrlInput(url: String) extends Msg
    case class PasswordInput(password: String) extends Msg
    case object Connect extends Msg
    case class NodeConnected(result: Either[ErrorJson, ClientNodeSessionJson])
        extends Msg
    case object Cancel extends Msg
    case object Incorporate extends Msg
    case class NodeIncorporated(result: Either[ErrorJson, ServerJson])
        extends Msg
  }

  def update(
      msg: Msg,
      model: Model,
      nodes: Nodes
  ): (Model, Nodes, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.HelpIntro(display) =>
        (model.copy(helpIntro = display), nodes, Seq.empty)

      case Msg.HelpConnect(display) =>
        (model.copy(helpConnect = display), nodes, Seq.empty)

      case Msg.NodeUrlInput(url) =>
        (model.copy(nodeUrl = url), nodes, Seq.empty)

      case Msg.PasswordInput(password) =>
        (model.copy(password = password), nodes, Seq.empty)

      case Msg.Connect =>
        (
          model.copy(connecting = true, connectingError = None),
          nodes,
          Seq(connect(model.nodeUrl, model.password))
        )

      case Msg.NodeConnected(Right(json)) => {
        val session = ClientNodeSession(json)
        if (nodes.containsServer(session.server.id))
          (
            model.copy(
              connecting = false,
              connectingError =
                Some("This node has already been registered as a server.")
            ),
            nodes,
            Seq.empty
          )
        else
          (
            model.copy(
              connecting = false,
              connectingError = None,
              nodeSession = Some(session)
            ),
            nodes.add(session.server),
            Seq.empty
          )
      }

      case Msg.NodeConnected(Left(e)) =>
        (
          model.copy(
            connecting = false,
            connectingError = Some(e.default_message)
          ),
          nodes,
          Seq(
            log_error("Node connecting error.", Some(js.JSON.stringify(e)))
          )
        )

      case Msg.Cancel =>
        (
          model.copy(
            connecting = false,
            connectingError = None,
            incorporatingError = None,
            nodeSession = None
          ),
          nodes,
          Seq.empty
        )

      case Msg.Incorporate =>
        (
          model.copy(incorporating = true, incorporatingError = None),
          nodes,
          Seq(addParentNode(model.nodeUrl, model.password))
        )

      case Msg.NodeIncorporated(Right(json)) =>
        (
          model.copy(incorporating = false, incorporatingError = None),
          nodes.addServer(Server(json)),
          Seq(Modal.close(classOf[Modal.Incorporate]))
        )

      case Msg.NodeIncorporated(Left(e)) =>
        (
          model.copy(
            incorporating = false,
            incorporatingError = Some(e.default_message)
          ),
          nodes,
          Seq(
            log_error("Node incorporating error.", Some(js.JSON.stringify(e)))
          )
        )
    }

  private def connect(url: String, password: String): Cmd[AppMsg] =
    Commands
      .send(Commands.TryConnectServerNode(url, password))
      .map(Msg.toApp(Msg.NodeConnected(_)))

  private def addParentNode(url: String, password: String): Cmd[AppMsg] =
    Commands
      .send(Commands.AddServerNode(url, password))
      .map(Msg.toApp(Msg.NodeIncorporated(_)))

  def apply(model: Model, dispatch: AppMsg => Unit)(implicit
      context: Context,
      domain: Domain
  ): ReactElement =
    dialog(
      className := "incorporate",
      open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          button(
            className := "close default",
            onClick := (_ =>
              dispatch(
                Modal.Msg.CloseModal(classOf[Modal.Incorporate]).toApp
              )
            )
          ),
          h1()(
            "Incorporate Remote Database",
            buttonHelp(
              model.helpIntro,
              () => dispatch(Msg.HelpIntro(true).toApp)
            )
          )
        ),
        div(className := "body")(
          sectionHelp(
            model.helpIntro,
            () => dispatch(Msg.HelpIntro(false).toApp),
            context.help.ModalIncorporate_intro
          ),
          model.nodeSession
            .map(sectionIncorporate(model, _, dispatch))
            .getOrElse(sectionConnect(model, domain.nodes, dispatch))
        )
      )
    )

  private def sectionConnect(
      model: Model,
      nodes: Nodes,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    section(className := "connect")(
      h2()(
        "Connect",
        buttonHelp(
          model.helpConnect,
          () => dispatch(Msg.HelpConnect(true).toApp)
        )
      ),
      model.connectingError.map(e => section(className := "error")(e)),
      form()(
        sectionHelp(
          model.helpConnect,
          () => dispatch(Msg.HelpConnect(false).toApp),
          context.help.ModalIncorporate_connect(
            nodes.operatingId.map(_.uuid).getOrElse("")
          )
        ),

        // Node URL
        div(className := "input-field")(
          label(htmlFor := "node-url")("Node URL"),
          input(
            `type` := "text",
            id := "node-url",
            name := "nodeUrl",
            placeholder := "https://example.com",
            value := model.nodeUrl,
            Validation.ariaInvalid(model.validateNodeUrl),
            onChange := ((e) =>
              dispatch(Msg.NodeUrlInput(e.target.value).toApp)
            )
          ),
          Validation.sectionValidationError(model.validateNodeUrl)
        ),

        // Password
        div(className := "input-field")(
          label(htmlFor := "password")("Password"),
          input(
            `type` := "password",
            id := "password",
            name := "password",
            value := model.password,
            onChange := (e => dispatch(Msg.PasswordInput(e.target.value).toApp))
          )
        ),

        // Preview
        div(className := "buttons")(
          button(
            `type` := "submit",
            disabled := !model.readyToConnect,
            aria - "busy" := model.connecting.toString(),
            onClick := (e => {
              e.preventDefault()
              dispatch(Msg.Connect.toApp)
            })
          )("Preview")
        )
      )
    )

  private def sectionIncorporate(
      model: Model,
      nodeSession: ClientNodeSession,
      dispatch: AppMsg => Unit
  ): ReactElement =
    section(className := "incorporate")(
      h2()("Node"),
      model.incorporatingError.map(e => section(className := "error")(e)),

      // Node preview
      section(className := "node-preview")(
        section(className := "node-name")(
          nodeImg(nodeSession.server),
          span(className := "name")(nodeSession.server.name)
        ),
        nodeSession.asChild.map(child =>
          section(className := "child-privileges")(
            "Privileges: ",
            span(className := "privileges")(
              if (child.asOwner)
                "Owner"
              else if (child.canEditLinks)
                "Post, Edit links"
              else
                "Post"
            )
          )
        ),
        nodeSession.serverRootCotonoma.map { case (_, coto) =>
          Option.when(coto.content.isDefined) {
            section(className := "node-description")(
              ViewCoto.sectionCotoContentDetails(coto)
            )
          }
        }
      ),

      // Incorporate button
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (e => dispatch(Msg.Cancel.toApp))
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToIncorporate,
          aria - "busy" := model.incorporating.toString(),
          onClick := (e => dispatch(Msg.Incorporate.toApp))
        )("Incorporate")
      )
    )
}
