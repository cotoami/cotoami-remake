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
    dialog(
      className := "operate-as",
      open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          h1()("Switch Operating Node")
        ),
        div(className := "body")(
        )
      )
    )
}
