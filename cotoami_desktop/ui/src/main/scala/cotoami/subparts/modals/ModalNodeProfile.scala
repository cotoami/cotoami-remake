package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.backend.{Coto, Node}
import cotoami.subparts.{imgNode, Modal}

object ModalNodeProfile {

  case class Model(
      node: Node,
      rootCoto: Option[Coto]
  )

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.NodeProfileMsg andThen AppMsg.ModalMsg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    (model, Seq.empty)

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "node-profile",
      closeButton = Some((classOf[Modal.NodeProfile], dispatch))
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
