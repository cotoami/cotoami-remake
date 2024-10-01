package cotoami.subparts.modals

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Msg => AppMsg}
import cotoami.models.{Coto, Id, Node}
import cotoami.repositories.Domain
import cotoami.components.toolButton
import cotoami.subparts.{imgNode, labeledField, Modal, ViewCoto}

object ModalNodeProfile {

  case class Model(
      nodeId: Id[Node],
      error: Option[String] = None
  ) {
    def isOperatingNode()(implicit context: Context): Boolean =
      context.domain.nodes.isOperating(nodeId)
  }

  object Model {
    def apply(nodeId: Id[Node]): (Model, Cmd[AppMsg]) =
      (
        Model(nodeId, None),
        Domain.fetchNodeDetails(nodeId)
      )
  }

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.NodeProfileMsg andThen AppMsg.ModalMsg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Domain, Cmd[AppMsg]) = {
    (model, context.domain, Cmd.none)
  }

  def apply(model: Model)(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): ReactElement =
    Modal.view(
      elementClasses = "node-profile",
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
      dispatch: AppMsg => Unit
  ): ReactElement =
    Fragment(
      div(className := "sidebar")(
        section(className := "node-icon")(
          imgNode(node),
          Option.when(model.isOperatingNode()) {
            toolButton(
              symbol = "edit",
              tip = "Edit",
              classes = "edit",
              onClick = _ =>
                dispatch(
                  Modal.Msg.OpenModal(Modal.NodeIcon()).toApp
                )
            )
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
            toolButton(
              symbol = "edit",
              tip = "Edit",
              classes = "edit"
            )
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
            toolButton(
              symbol = "edit",
              tip = "Edit",
              classes = "edit"
            )
          )
        }
      )
    )

  private def fieldClientNodes(model: Model)(implicit
      context: Context
  ): ReactElement =
    labeledField(
      classes = "client-nodes",
      label = "Client nodes",
      labelFor = Some("node-profile-client-nodes")
    )(
      div(className := "input-with-tools")(
        section(className := "client-nodes-count")(
          code(className := "connecting")(
            context.domain.nodes.activeClients.count
          ),
          "connecting",
          span(className := "separator")("/"),
          code(className := "nodes")("0"),
          "nodes"
        ),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            toolButton(
              symbol = "edit",
              tip = "Edit",
              classes = "edit"
            )
          )
        }
      )
    )
}
