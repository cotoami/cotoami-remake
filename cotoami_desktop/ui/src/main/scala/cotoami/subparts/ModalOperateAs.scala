package cotoami.subparts

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.backend.Node

object ModalOperateAs {

  case class Model(
      current: Node,
      switchingTo: Node
  )

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.OperateAsMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    (model, Seq.empty)

  def apply(
      model: Model,
      dispatch: AppMsg => Unit
  ): ReactElement =
    Modal.view(
      modalType = classOf[Modal.OperateAs],
      elementClasses = "operate-as",
      closeButton = true,
      dispatch = dispatch
    )(
      "Switch Operating Node"
    )(
      section(className := "preview")(
        p("You are about to switch the operating node as below:")
      )
    )
}
