package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.backend.Node
import cotoami.subparts.Modal

object ModalNodeProfile {

  case class Model(
      node: Node
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
      "Node"
    )(
      section()()
    )
}
