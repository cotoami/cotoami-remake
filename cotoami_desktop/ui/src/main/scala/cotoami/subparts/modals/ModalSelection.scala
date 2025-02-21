package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Coto
import cotoami.repository.Cotos
import cotoami.components.{toolButton, Flipped, Flipper, ScrollArea}
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
  ): ReactElement = {
    val cotos = context.repo.cotos
    Modal.view(
      dialogClasses = "selection",
      closeButton = Some((classOf[Modal.Selection.type], dispatch))
    )(
      "Selected cotos"
    )(
      ScrollArea(className = Some("scroll-selected-cotos"))(
        Flipper(
          element = "div",
          className = "selected-cotos",
          flipKey = cotos.selectedIds.length.toString()
        )(
          cotos.selected.map(coto =>
            Flipped(key = coto.id.uuid, flipId = coto.id.uuid)(
              articleCoto(coto)
            ): ReactElement
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

  private def articleCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    article(className := "coto")(
      header()(
        toolButton(
          classes = "deselect",
          symbol = "check_box",
          tip = Some("Deselect"),
          tipPlacement = "right",
          onClick = e => {
            e.stopPropagation()
            dispatch(AppMsg.Deselect(coto.id))
          }
        )
      ),
      ViewCoto.divContent(coto)
    )
}
