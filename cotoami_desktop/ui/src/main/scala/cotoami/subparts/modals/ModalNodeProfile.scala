package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{log_error, Context, Msg => AppMsg}
import cotoami.backend.{Coto, ErrorJson, Node, NodeDetails}
import cotoami.components.ToolButton
import cotoami.subparts.{imgNode, Modal, ViewCoto}

object ModalNodeProfile {

  case class Model(
      node: Node,
      rootCoto: Option[Coto],
      error: Option[String] = None
  ) {
    def isOperatingNode()(implicit context: Context): Boolean =
      context.domain.nodes.isOperating(this.node.id)
  }

  object Model {
    def apply(node: Node): (Model, Seq[Cmd[AppMsg]]) =
      (
        Model(node, None),
        Seq(
          NodeDetails.fetch(node.id)
            .map(Msg.toApp(Msg.NodeDetailsFetched(_)))
        )
      )
  }

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.NodeProfileMsg andThen AppMsg.ModalMsg

    case class NodeDetailsFetched(result: Either[ErrorJson, NodeDetails])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.NodeDetailsFetched(Right(details)) => {
        (
          model.copy(node = details.node, rootCoto = details.root.map(_._2)),
          Seq.empty
        )
      }

      case Msg.NodeDetailsFetched(Left(e)) =>
        (
          model.copy(error = Some(e.default_message)),
          Seq(
            log_error("Node connecting error.", Some(js.JSON.stringify(e)))
          )
        )
    }

  def apply(model: Model, dispatch: AppMsg => Unit)(implicit
      context: Context
  ): ReactElement =
    Modal.view(
      elementClasses = "node-profile",
      closeButton = Some((classOf[Modal.NodeProfile], dispatch)),
      error = model.error
    )(
      "Node Profile"
    )(
      div(className := "sidebar")(
        section(className := "node-icon")(
          imgNode(model.node),
          Option.when(model.isOperatingNode()) {
            ToolButton(
              classes = "edit",
              tip = "Edit",
              symbol = "edit",
              onClick = () =>
                dispatch(
                  Modal.Msg.OpenModal(Modal.Image("Change Node Icon")).toApp
                )
            )
          }
        )
      ),
      div(className := "settings")(
        div(className := "input-field node-id")(
          label(htmlFor := "node-profile-id")("ID"),
          input(
            `type` := "text",
            id := "node-profile-id",
            name := "nodeId",
            readOnly := true,
            value := model.node.id.uuid
          )
        ),
        fieldName(model, dispatch),
        fieldDescription(model, dispatch)
      )
    )

  private def fieldName(model: Model, dispatch: AppMsg => Unit)(implicit
      context: Context
  ): ReactElement =
    div(className := "input-field node-name")(
      label(htmlFor := "node-profile-name")("Name"),
      div(className := "input-with-tools")(
        input(
          `type` := "text",
          id := "node-profile-name",
          name := "nodeName",
          readOnly := true,
          value := model.node.name
        ),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            ToolButton(
              classes = "edit",
              tip = "Edit",
              symbol = "edit"
            )
          )
        }
      )
    )

  private def fieldDescription(model: Model, dispatch: AppMsg => Unit)(implicit
      context: Context
  ): ReactElement =
    model.rootCoto.map(coto =>
      div(className := "input-field node-description")(
        label(htmlFor := "node-profile-description")("Description"),
        div(className := "input-with-tools")(
          section(className := "node-description")(
            ViewCoto.sectionCotoContentDetails(coto)
          ),
          Option.when(model.isOperatingNode()) {
            div(className := "tools")(
              ToolButton(
                classes = "edit",
                tip = "Edit",
                symbol = "edit"
              )
            )
          }
        )
      )
    )
}
