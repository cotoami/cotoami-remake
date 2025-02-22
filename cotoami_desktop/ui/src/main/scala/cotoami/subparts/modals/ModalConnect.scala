package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.models.{Coto, Id, Link}
import cotoami.components.materialSymbol
import cotoami.subparts.Modal

object ModalConnect {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      cotoId: Id[Coto],
      error: Option[String] = None
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ConnectMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "connect",
      closeButton = Some((classOf[Modal.Connect], dispatch)),
      error = model.error
    )(
      materialSymbol(Link.ConnectIconName),
      "Connect"
    )(
    )
}
