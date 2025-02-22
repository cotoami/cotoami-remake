package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Coto
import cotoami.repository.Cotos
import cotoami.components.{
  materialSymbol,
  toolButton,
  Flipped,
  Flipper,
  ScrollArea
}
import cotoami.subparts.{Modal, ViewCoto}

object ModalSelection {

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.SelectionMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Clear extends Msg
  }

  def update(msg: Msg)(implicit
      context: Context
  ): (Cotos, Cmd[AppMsg]) =
    msg match {
      case Msg.Clear =>
        (
          context.repo.cotos.clearSelection,
          Modal.close(Modal.Selection.getClass())
        )
    }

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
      materialSymbol("check_box"),
      s"Selected cotos (${cotos.selectedIds.size})"
    )(
      ScrollArea(className = Some("selected-cotos"))(
        Flipper(
          element = "section",
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
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Msg.Clear.into))
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
          onClick = _ => dispatch(AppMsg.Deselect(coto.id))
        )
      ),
      ViewCoto.divContent(coto)
    )
}
