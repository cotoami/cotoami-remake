package cotoami.subparts

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.backend.Node
import cotoami.components.materialSymbol

object ModalOperateAs {

  case class Model(
      current: Node,
      switchingTo: Node,
      switching: Boolean = false
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
      elementClasses = "operate-as",
      closeButton = Some((classOf[Modal.OperateAs], dispatch))
    )(
      "Switch Operating Node"
    )(
      section(className := "preview")(
        p("You are about to switch the operating node as below:"),
        section(className := "current")(
          spanNode(model.current)
        ),
        div(className := "arrow")(materialSymbol("arrow_downward", "arrow")),
        section(className := "switching-to")(
          spanNode(model.switchingTo)
        )
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline"
        )("Cancel"),
        button(
          `type` := "button",
          aria - "busy" := model.switching.toString()
        )("Switch")
      )
    )
}
