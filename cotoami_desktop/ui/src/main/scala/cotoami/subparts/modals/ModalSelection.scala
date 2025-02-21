package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.repository.Cotos
import cotoami.subparts.Modal

object ModalSelection {

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.SelectionMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg)(implicit
      context: Context
  ): (Cotos, Cmd[AppMsg]) =
    (context.repo.cotos, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply()(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "selected-cotos",
      closeButton = Some((classOf[Modal.Selection.type], dispatch))
    )(
      "Selected cotos"
    )(
    )
}
