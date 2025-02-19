package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ClientNode, Coto, Id, Node, Page, Server}
import cotoami.repository.{Nodes, Root}
import cotoami.backend.{ClientNodeBackend, Commands, ErrorJson, LocalServer}
import cotoami.components.{materialSymbol, toolButton}
import cotoami.subparts.{
  imgNode,
  labeledField,
  labeledInputField,
  sectionClientNodesCount,
  Modal,
  ViewCoto
}

object ModalNodeProfile {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      clientCount: Double = 0,
      localServer: Option[LocalServer] = None,
      enablingAnonymousRead: Boolean = false,
      error: Option[String] = None
  ) {
    def isOperatingNode()(implicit context: Context): Boolean =
      context.repo.nodes.isOperating(nodeId)

    def anonymousReadEnabled: Boolean =
      localServer.map(_.anonymousReadEnabled).getOrElse(false)
  }

  object Model {
    def apply(nodeId: Id[Node], nodes: Nodes): (Model, Cmd[AppMsg]) =
      (
        Model(nodeId),
        Cmd.Batch(
          Root.fetchNodeDetails(nodeId),
          fetchClientCount,
          if (nodes.isOperating(nodeId))
            LocalServer.fetch.map(Msg.LocalServerFetched(_).into)
          else
            Cmd.none
        )
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class LocalServerFetched(result: Either[ErrorJson, LocalServer])
        extends Msg
    case class ClientCountFetched(result: Either[ErrorJson, Page[ClientNode]])
        extends Msg
    case class EnableAnonymousRead(enable: Boolean) extends Msg
    case class AnonymousReadEnabled(result: Either[ErrorJson, Boolean])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) = {
    val default = (model, Cmd.none)
    msg match {
      case Msg.LocalServerFetched(Right(server)) =>
        default.copy(_1 = model.copy(localServer = Some(server)))

      case Msg.LocalServerFetched(Left(e)) =>
        default.copy(_2 = cotoami.error("Couldn't fetch the local server.", e))

      case Msg.ClientCountFetched(Right(page)) =>
        default.copy(_1 = model.copy(clientCount = page.totalItems))

      case Msg.ClientCountFetched(Left(e)) =>
        default.copy(_2 = cotoami.error("Couldn't fetch client count.", e))

      case Msg.EnableAnonymousRead(enable) =>
        default.copy(
          _1 = model.copy(enablingAnonymousRead = true),
          _2 = enableAnonymousRead(enable).map(Msg.AnonymousReadEnabled(_).into)
        )

      case Msg.AnonymousReadEnabled(Right(enabled)) =>
        default.copy(
          _1 = model
            .modify(_.enablingAnonymousRead).setTo(false)
            .modify(_.localServer.each.anonymousReadEnabled).setTo(enabled)
        )

      case Msg.AnonymousReadEnabled(Left(e)) =>
        default.copy(_2 =
          cotoami.error("Couldn't enable/disable anonymous read.", e)
        )
    }
  }

  def fetchClientCount: Cmd.One[AppMsg] =
    ClientNodeBackend.fetchRecent(0, Some(1))
      .map(Msg.ClientCountFetched(_).into)

  def enableAnonymousRead(
      enable: Boolean
  ): Cmd.One[Either[ErrorJson, Boolean]] =
    Commands.send(Commands.EnableAnonymousRead(enable))

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "node-profile",
      closeButton = Some((classOf[Modal.NodeProfile], dispatch)),
      error = model.error
    )(
      "Node Profile"
    )(
      context.repo.nodes.get(model.nodeId)
        .map(modalContent(_, model))
        .getOrElse(s"Node ${model.nodeId} not found.")
    )

  private def modalContent(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val rootCoto = context.repo.rootOf(model.nodeId).map(_._2)
    val asServer = context.repo.nodes.servers.get(model.nodeId)
    Fragment(
      divSidebar(node, model),
      div(className := "fields")(
        fieldId(node),
        fieldName(node, rootCoto, model),
        asServer.map(fieldServerUrl),
        rootCoto.map(fieldDescription(_, model)),
        model.localServer.flatMap(_.activeConfig).map(config =>
          section(className := "local-server")(
            h2()("Local server"),
            fieldClientNodes(model),
            fieldAnonymousRead(model)
          )
        )
      )
    )
  }

  private def divSidebar(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "sidebar")(
      section(className := "node-icon")(
        imgNode(node),
        Option.when(model.isOperatingNode()) {
          buttonEdit(_ => dispatch(Modal.Msg.OpenModal(Modal.NodeIcon())))
        }
      ),
      Option.when(!context.repo.nodes.isOperating(node.id)) {
        section(className := "operating-node")(
          div(className := "arrow")(materialSymbol("arrow_upward")),
          section(className := "privileges")(
            context.repo.nodes.childPrivilegesTo(node.id) match {
              case Some(privileges) => {
                if (privileges.asOwner)
                  "Owner"
                else if (privileges.canEditLinks)
                  "Post, Edit links"
                else
                  "Post"
              }
              case None => "Read-only"
            }
          ),
          context.repo.nodes.operating.map(imgNode(_))
        )
      }
    )

  private def fieldId(node: Node): ReactElement =
    labeledInputField(
      classes = "node-id",
      label = "ID",
      inputId = "node-profile-id",
      inputType = "text",
      inputValue = node.id.uuid,
      readOnly = true
    )

  private def fieldName(node: Node, rootCoto: Option[Coto], model: Model)(
      implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    labeledField(
      classes = "node-name",
      label = "Name",
      labelFor = Some("node-profile-name")
    )(
      div(className := "input-with-tools")(
        input(
          `type` := "text",
          id := "node-profile-name",
          name := "nodeName",
          readOnly := true,
          value := node.name
        ),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            rootCoto.map(buttonEditRootCoto)
          )
        }
      )
    )

  private def fieldServerUrl(server: Server): ReactElement =
    labeledField(
      classes = "server-url",
      label = "Server URL",
      labelFor = Some("node-profile-server-url")
    )(
      div(className := "input-with-tools")(
        input(
          `type` := "text",
          id := "node-profile-server-url",
          readOnly := true,
          value := server.server.urlPrefix
        ),
        div(className := "tools")(
          buttonEdit(_ => ())
        )
      )
    )

  private def fieldDescription(rootCoto: Coto, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    labeledField(
      classes = "node-description",
      label = "Description",
      labelFor = Some("node-profile-description")
    )(
      div(className := "input-with-tools")(
        section(className := "node-description")(
          ViewCoto.sectionCotonomaContent(rootCoto)
        ),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            buttonEditRootCoto(rootCoto)
          )
        }
      )
    )

  private def fieldClientNodes(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    labeledField(
      classes = "client-nodes",
      label = "Client nodes",
      labelFor = Some("node-profile-client-nodes")
    )(
      div(className := "input-with-tools")(
        sectionClientNodesCount(model.clientCount, context.repo.nodes),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            buttonEdit(_ =>
              dispatch(
                (Modal.Msg.OpenModal.apply _).tupled(
                  Modal.Clients()
                )
              )
            )
          )
        }
      )
    )

  private def fieldAnonymousRead(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    labeledField(
      classes = "anonymous-read",
      label = "Anonymous read",
      labelFor = Some("node-profile-anonymous-read")
    )(
      input(
        `type` := "checkbox",
        role := "switch",
        checked := model.anonymousReadEnabled,
        disabled := model.enablingAnonymousRead,
        onChange := (_ =>
          if (model.anonymousReadEnabled)
            dispatch(Msg.EnableAnonymousRead(false)) // disable
          else
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  "Are you sure you want to allow anonymous read-only access?" ++
                    " (Anyone who knows this node's URL can view your cotos and links.)",
                  Msg.EnableAnonymousRead(true) // enable
                )
              )
            )
        )
      ),
      Option.when(model.enablingAnonymousRead) {
        span(className := "processing", aria - "busy" := "true")()
      }
    )

  private def buttonEditRootCoto(rootCoto: Coto)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    buttonEdit(_ =>
      dispatch(
        (Modal.Msg.OpenModal.apply _).tupled(
          Modal.CotoEditor(rootCoto)
        )
      )
    )

  private def buttonEdit(
      onClick: SyntheticMouseEvent[_] => Unit
  ): ReactElement =
    toolButton(
      symbol = "edit",
      tip = Some("Edit"),
      classes = "edit",
      onClick = onClick
    )
}
