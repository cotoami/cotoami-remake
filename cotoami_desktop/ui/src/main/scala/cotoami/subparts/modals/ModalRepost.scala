package cotoami.subparts.modals

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.models.{Coto, Id}
import cotoami.subparts.Modal

object ModalRepost {

  case class Model(
      cotoId: Id[Coto]
  )

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "repost",
      closeButton = Some((classOf[Modal.Repost], dispatch))
    )(
      "Repost"
    )(
    )
}
