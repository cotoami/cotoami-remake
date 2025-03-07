package cotoami.subparts.modals

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma}
import cotoami.components.materialSymbol
import cotoami.subparts.Modal

object ModalPromote {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      coto: Coto,
      error: Option[String] = None
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "promote",
      closeButton = Some((classOf[Modal.Promote], dispatch)),
      error = model.error
    )(
      materialSymbol(Cotonoma.IconName),
      "Promote to Cotonoma"
    )(
    )
}
