package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.subparts.Modal

object ModalImage {

  case class Model(
      title: String
  )

  sealed trait Msg {
    def toApp: AppMsg =
      Modal.Msg.ImageMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    (model, Seq.empty)

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "image",
      closeButton = Some((classOf[Modal.Image], dispatch))
    )(
      model.title
    )(
      section()()
    )
}
