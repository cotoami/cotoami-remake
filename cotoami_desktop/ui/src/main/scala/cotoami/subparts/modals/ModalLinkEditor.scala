package cotoami.subparts.modals

import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Link
import cotoami.subparts.Modal

object ModalLinkEditor {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      original: Link
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) = (model, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Modal.view(
      dialogClasses = "link-editor",
      closeButton = Some((classOf[Modal.LinkEditor], dispatch))
    )(
      if (context.domain.isPin(model.original))
        "Pin"
      else
        "Link"
    )(
      "Edit a link here."
    )
}
