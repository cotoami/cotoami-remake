package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.repository.Cotos
import cotoami.subparts.{Modal, ViewCoto}

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
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "selection",
      closeButton = Some((classOf[Modal.Selection.type], dispatch))
    )(
      "Selected cotos"
    )(
      ul(className := "selected-cotos")(
        context.repo.cotos.selected.map(coto =>
          li(className := "selected-coto")(
            ViewCoto.divContent(coto)
          )
        )
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline"
        )("Clear selection")
      )
    )
}
