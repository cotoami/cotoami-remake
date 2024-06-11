package cotoami.subparts

import scala.scalajs.js
import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.log_error
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

object ModalAddNode {

  case class Model(
      nodeUrl: String = "",
      password: String = "",
      error: Option[String] = None,
      connecting: Boolean = false,
      nodeSession: Option[ClientNodeSession] = None,
      connectingError: Option[String] = None,
      adding: Boolean = false,
      addingError: Option[String] = None
  ) {
    def validateNodeUrl: Validation.Result =
      if (this.nodeUrl.isBlank())
        Validation.Result.toBeValidated
      else
        Validation.Result(ServerNode.validateUrl(this.nodeUrl))

    def readyToConnect: Boolean = !this.connecting && validateNodeUrl.validated

    def readyToAdd: Boolean = !this.connecting && !this.adding
  }

  sealed trait Msg {
    def asAppMsg: cotoami.Msg = Modal.AddNodeMsg(this).pipe(cotoami.ModalMsg)
  }

  private def appMsgTagger[T](tagger: T => Msg): T => cotoami.Msg =
    tagger andThen Modal.AddNodeMsg andThen cotoami.ModalMsg

  case class NodeUrlInput(url: String) extends Msg
  case class PasswordInput(password: String) extends Msg
  case object Connect extends Msg
  case class NodeConnected(result: Either[ErrorJson, ClientNodeSessionJson])
      extends Msg
  case object Cancel extends Msg
  case object Add extends Msg
  case class NodeAdded(result: Either[ErrorJson, ServerJson]) extends Msg

  def update(
      msg: Msg,
      model: Model,
      nodes: Nodes
  ): (Model, Nodes, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case NodeUrlInput(url) =>
        (model.copy(nodeUrl = url), nodes, Seq.empty)

      case PasswordInput(password) =>
        (model.copy(password = password), nodes, Seq.empty)

      case Connect =>
        (
          model.copy(connecting = true, connectingError = None),
          nodes,
          Seq(connect(model.nodeUrl, model.password))
        )

      case NodeConnected(Right(json)) => {
        val session = ClientNodeSession(json)
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

      case NodeConnected(Left(e)) =>
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

      case Cancel =>
        (
          model.copy(
            connecting = false,
            connectingError = None,
            addingError = None,
            nodeSession = None
          ),
          nodes,
          Seq.empty
        )

      case Add =>
        (
          model.copy(adding = true, addingError = None),
          nodes,
          Seq(addParentNode(model.nodeUrl, model.password))
        )

      case NodeAdded(Right(json)) =>
        (
          model.copy(adding = false, addingError = None),
          nodes.prependServer(Server(json)),
          Seq(Modal.close(classOf[Modal.AddNode]))
        )

      case NodeAdded(Left(e)) =>
        (
          model.copy(
            adding = false,
            addingError = Some(e.default_message)
          ),
          nodes,
          Seq(log_error("Node adding error.", Some(js.JSON.stringify(e))))
        )
    }

  private def connect(url: String, password: String): Cmd[cotoami.Msg] =
    Commands
      .send(Commands.TryConnectServerNode(url, password, false))
      .map(appMsgTagger(NodeConnected(_)))

  private def addParentNode(url: String, password: String): Cmd[cotoami.Msg] =
    Commands
      .send(Commands.AddServerNode(url, password, false))
      .map(appMsgTagger(NodeAdded(_)))

  def apply(
      model: Model,
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    dialog(
      className := "add-node",
      open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          button(
            className := "close default",
            onClick := (_ =>
              dispatch(Modal.CloseModal(classOf[Modal.AddNode]).asAppMsg)
            )
          ),
          h1()("Add Node")
        ),
        model.error.map(e => section(className := "error")(e)),
        div(className := "body")(
          section(className := "introduction")(
            """
            You can incorporate another database node into your database.
            Once incorporated, it will sync with the original node 
            in real-time as long as you are online.
            """
          ),
          model.nodeSession
            .map(sectionAdd(model, _, domain, dispatch))
            .getOrElse(sectionConnect(model, dispatch))
        )
      )
    )

  private def sectionConnect(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "connect")(
      h2()("Connect"),
      model.connectingError.map(e => section(className := "error")(e)),
      form()(
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
            onChange := ((e) => dispatch(NodeUrlInput(e.target.value).asAppMsg))
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
            onChange := (e => dispatch(PasswordInput(e.target.value).asAppMsg))
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
              dispatch(Connect.asAppMsg)
            })
          )("Preview")
        )
      )
    )

  private def sectionAdd(
      model: Model,
      nodeSession: ClientNodeSession,
      domain: Domain,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "add")(
      h2()("Node"),
      model.addingError.map(e => section(className := "error")(e)),

      // Node preview
      section(className := "node-preview")(
        section(className := "node-name")(
          nodeImg(nodeSession.server),
          span(className := "name")(nodeSession.server.name)
        ),
        nodeSession.serverRootCotonoma.map { case (_, coto) =>
          section(className := "node-content")(
            ViewCoto.sectionCotoContentDetails(coto)
          )
        }
      ),

      // Add button
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (e => dispatch(Cancel.asAppMsg))
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToAdd,
          aria - "busy" := model.adding.toString(),
          onClick := (e => dispatch(Add.asAppMsg))
        )("Add")
      )
    )
}
