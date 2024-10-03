package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import fui.Cmd
import cotoami.{log_error, Context, Into, Msg => AppMsg}
import cotoami.models.{ClientNode, Coto, Id, Node, Page}
import cotoami.repositories.Domain
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.components.toolButton
import cotoami.subparts.{imgNode, labeledField, Modal, ViewCoto}

object ModalNodeProfile {

  case class Model(
      nodeId: Id[Node],
      clientCount: Double = 0,
      error: Option[String] = None
  ) {
    def isOperatingNode()(implicit context: Context): Boolean =
      context.domain.nodes.isOperating(nodeId)
  }

  object Model {
    def apply(nodeId: Id[Node]): (Model, Cmd[AppMsg]) =
      (
        Model(nodeId),
        Cmd.Batch(
          Domain.fetchNodeDetails(nodeId),
          fetchClientCount
        )
      )
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class ClientCountFetched(result: Either[ErrorJson, Page[ClientNode]])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) = {
    msg match {
      case Msg.ClientCountFetched(Right(page)) =>
        (model.copy(clientCount = page.totalItems), Cmd.none)

      case Msg.ClientCountFetched(Left(e)) =>
        (
          model,
          log_error(
            "Couldn't fetch client count.",
            Some(js.JSON.stringify(e))
          )
        )
    }
  }

  def fetchClientCount: Cmd.One[AppMsg] =
    ClientNodeBackend.fetchRecent(0, Some(1))
      .map(Msg.ClientCountFetched(_).into)

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
      context.domain.nodes.get(model.nodeId)
        .map(modalContent(_, model))
        .getOrElse(s"Node ${model.nodeId} not found.")
    )

  private def modalContent(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
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
        context.domain.rootOf(model.nodeId).map { case (_, coto) =>
          fieldDescription(coto, model)
        },
        Option.when(model.isOperatingNode()) {
          fieldClientNodes(model)
        }
      )
    )

  private def fieldId(node: Node): ReactElement =
    labeledField(
      classes = "node-id",
      label = "ID",
      labelFor = Some("node-profile-id")
    )(
      input(
        `type` := "text",
        id := "node-profile-id",
        name := "nodeId",
        readOnly := true,
        value := node.id.uuid
      )
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

  private def fieldDescription(rootCoto: Coto, model: Model)(implicit
      context: Context
  ): ReactElement =
    labeledField(
      classes = "node-description",
      label = "Description",
      labelFor = Some("node-profile-description")
    )(
      div(className := "input-with-tools")(
        ViewCoto.sectionNodeDescription(rootCoto).getOrElse(
          section(className := "node-description")()
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
  ): ReactElement = {
    val connecting = context.domain.nodes.activeClients.count
    labeledField(
      classes = "client-nodes",
      label = "Client nodes",
      labelFor = Some("node-profile-client-nodes")
    )(
      div(className := "input-with-tools")(
        section(className := "client-nodes-count")(
          Option.when(connecting > 0) {
            Fragment(
              code(className := "connecting")(
                context.domain.nodes.activeClients.count
              ),
              "connecting",
              span(className := "separator")("/")
            )
          },
          code(className := "nodes")(model.clientCount),
          "nodes"
        ),
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
  }

  private def buttonEdit(
      onClick: SyntheticMouseEvent[_] => Unit
  ): ReactElement =
    toolButton(
      symbol = "edit",
      tip = "Edit",
      classes = "edit",
      onClick = onClick
    )
}
