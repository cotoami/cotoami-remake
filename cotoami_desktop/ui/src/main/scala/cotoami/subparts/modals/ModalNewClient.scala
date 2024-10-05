package cotoami.subparts.modals

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalNewClient {

  case class Model(
      nodeId: String = "",
      canEditLinks: Boolean = false,
      asOwner: Boolean = false,
      error: Option[String] = None
  )

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "new-client",
      closeButton = Some((classOf[Modal.NewClient], dispatch)),
      error = model.error
    )(
      "New client"
    )(
    )
}
