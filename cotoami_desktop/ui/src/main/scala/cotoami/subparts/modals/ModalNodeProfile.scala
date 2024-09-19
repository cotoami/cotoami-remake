package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import com.softwaremill.quicklens._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{log_error, Context, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.repositories.Domain
import cotoami.backend.{ErrorJson, NodeDetails}
import cotoami.components.toolButton
import cotoami.subparts.{imgNode, Modal, ViewCoto}

object ModalNodeProfile {

  case class Model(
      nodeId: Id[Node],
      error: Option[String] = None
  ) {
    def isOperatingNode()(implicit context: Context): Boolean =
      context.domain.nodes.isOperating(this.nodeId)
  }

  object Model {
    def apply(nodeId: Id[Node]): (Model, Seq[Cmd[AppMsg]]) =
      (
        Model(nodeId, None),
        Seq(fetchNodeDetails(nodeId))
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

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Domain, Seq[Cmd[AppMsg]]) = {
    val default = (model, context.domain, Seq.empty)
    msg match {
      case Msg.NodeDetailsFetched(Right(details)) =>
        details.root match {
          case Some((cotonoma, coto)) =>
            default.copy(
              _2 = context.domain
                .modify(_.cotonomas).using(_.put(cotonoma))
                .modify(_.cotos).using(_.put(coto))
            )
          case None => default
        }

      case Msg.NodeDetailsFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(error = Some(e.default_message)),
          _3 = Seq(
            log_error("Node connecting error.", Some(js.JSON.stringify(e)))
          )
        )
    }
  }

  private def fetchNodeDetails(id: Id[Node]): Cmd[AppMsg] =
    NodeDetails.fetch(id)
      .map(Msg.toApp(Msg.NodeDetailsFetched(_)))

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
      div(className := "settings")(
        div(className := "input-field node-id")(
          label(htmlFor := "node-profile-id")("ID"),
          input(
            `type` := "text",
            id := "node-profile-id",
            name := "nodeId",
            readOnly := true,
            value := node.id.uuid
          )
        ),
        fieldName(node, model),
        fieldDescription(model)
      )
    )

  private def fieldName(node: Node, model: Model)(implicit
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

  private def fieldDescription(model: Model)(implicit
      context: Context
  ): ReactElement =
    context.domain.rootOf(model.nodeId).map { case (_, coto) =>
      div(className := "input-field node-description")(
        label(htmlFor := "node-profile-description")("Description"),
        div(className := "input-with-tools")(
          ViewCoto.sectionNodeDescription(coto),
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
}
