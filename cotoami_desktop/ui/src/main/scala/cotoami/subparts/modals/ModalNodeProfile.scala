package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ClientNode, Coto, Id, Node, Page, Server}
import cotoami.repository.Root
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.components.toolButton
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
      enablingAnonymousRead: Boolean = false,
      error: Option[String] = None
  ) {
    def isOperatingNode()(implicit context: Context): Boolean =
      context.repo.nodes.isOperating(nodeId)
  }

  object Model {
    def apply(nodeId: Id[Node]): (Model, Cmd[AppMsg]) =
      (
        Model(nodeId),
        Cmd.Batch(
          Root.fetchNodeDetails(nodeId),
          fetchClientCount
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
    case class ClientCountFetched(result: Either[ErrorJson, Page[ClientNode]])
        extends Msg
    case class AnonymousReadEnabled(result: Either[ErrorJson, Boolean])
        extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Root, Cmd[AppMsg]) = {
    val default = (model, context.repo, Cmd.none)
    msg match {
      case Msg.ClientCountFetched(Right(page)) =>
        default.copy(_1 = model.copy(clientCount = page.totalItems))

      case Msg.ClientCountFetched(Left(e)) =>
        default.copy(_3 = cotoami.error("Couldn't fetch client count.", e))

      case Msg.AnonymousReadEnabled(Right(enabled)) =>
        default.copy(
          _1 = model.copy(enablingAnonymousRead = false),
          _2 = context.repo.copy(anonymousReadEnabled = enabled)
        )

      case Msg.AnonymousReadEnabled(Left(e)) =>
        default.copy(_3 =
          cotoami.error("Couldn't enable/disable anonymous read.", e)
        )
    }
  }

  def fetchClientCount: Cmd.One[AppMsg] =
    ClientNodeBackend.fetchRecent(0, Some(1))
      .map(Msg.ClientCountFetched(_).into)

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
    val asServer = context.repo.nodes.servers.get(model.nodeId)
    Fragment(
      div(className := "sidebar")(
        section(className := "node-icon")(
          imgNode(node),
          Option.when(model.isOperatingNode()) {
            buttonEdit(_ => dispatch(Modal.Msg.OpenModal(Modal.NodeIcon())))
          }
        )
      ),
      div(className := "fields")(
        fieldId(node),
        fieldName(node, model),
        asServer.map(fieldServerUrl),
        context.repo.rootOf(model.nodeId).map { case (_, coto) =>
          fieldDescription(coto, model)
        },
        Option.when(model.isOperatingNode()) {
          Fragment(
            fieldClientNodes(model),
            fieldAnonymousRead(model)
          )
        }
      )
    )
  }

  private def fieldId(node: Node): ReactElement =
    labeledInputField(
      classes = "node-id",
      label = "ID",
      inputId = "node-profile-id",
      inputType = "text",
      inputValue = node.id.uuid,
      readOnly = true
    )

  private def fieldName(node: Node, model: Model)(implicit
      context: Context
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
            buttonEdit(_ => ())
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
      context: Context
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
            buttonEdit(_ => ())
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

  private def fieldAnonymousRead(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    labeledField(
      classes = "anonymous-read",
      label = "Anonymous read",
      labelFor = Some("node-profile-anonymous-read")
    )(
      input(
        `type` := "checkbox",
        role := "switch",
        checked := context.repo.anonymousReadEnabled,
        disabled := model.enablingAnonymousRead,
        onChange := (_ => ())
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
