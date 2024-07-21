package cotoami.subparts.modals

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}

object ModalImage {

  case class Model(
      title: String
  )

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    (model, Seq.empty)

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    div()()
}
