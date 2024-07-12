package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{log_error, Msg => AppMsg}
import cotoami.backend.{
  Commands,
  Coto,
  ErrorJson,
  Node,
  NodeDetails,
  NodeDetailsJson
}
import cotoami.subparts.{imgNode, Modal}

object ModalNodeProfile {

  case class Model(
      node: Node,
      rootCoto: Option[Coto],
      error: Option[String] = None
  )

  object Model {
    def apply(node: Node): (Model, Seq[Cmd[AppMsg]]) =
      (
        Model(node, None),
        Seq(
          Commands
            .send(Commands.NodeDetails(node.id))
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

    case class NodeDetailsFetched(result: Either[ErrorJson, NodeDetailsJson])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.NodeDetailsFetched(Right(json)) => {
        val details = NodeDetails(json)
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

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "node-profile",
      closeButton = Some((classOf[Modal.NodeProfile], dispatch)),
      error = model.error
    )(
      "Node Profile"
    )(
      div(className := "sidebar")(
        section(className := "node-icon")(imgNode(model.node))
      ),
      div(className := "settings")(
        // Name
        div(className := "input-field node-name")(
          label(htmlFor := "node-name")("Name"),
          input(
            `type` := "text",
            id := "node-name",
            name := "nodeName",
            readOnly := true,
            value := model.node.name
          )
        ),

        // ID
        div(className := "input-field")(
          label(htmlFor := "node-id")("ID"),
          input(
            `type` := "text",
            id := "node-id",
            name := "nodeId",
            readOnly := true,
            value := model.node.id.uuid
          )
        )
      )
    )
}
