package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.components.{toolButton, Flipped, Flipper, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Coto
import cotoami.repository.Cotos
import cotoami.subparts.{Modal, PartsCoto}

object ModalSelection {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(enableClear: Boolean = true)

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.SelectionMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Clear extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotos, Cmd[AppMsg]) =
    msg match {
      case Msg.Clear =>
        (
          model,
          context.repo.cotos.clearSelection,
          Cmd.Batch(
            Modal.close(classOf[Modal.Selection]),
            Modal.close(classOf[Modal.NewIto])
          )
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val cotos = context.repo.cotos
    Modal.view(
      dialogClasses = "selection",
      closeButton = Some((classOf[Modal.Selection], dispatch))
    )(
      Modal.spanTitleIcon("check_box"),
      s"${context.i18n.text.ModalSelection_title} (${cotos.selectedIds.size})"
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
      Option.when(model.enableClear) {
        div(className := "buttons")(
          button(
            `type` := "button",
            className := "cancel contrast outline",
            onClick := (_ => dispatch(Msg.Clear.into))
          )(context.i18n.text.ModalSelection_clear)
        )
      }
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
      PartsCoto.divContentPreview(coto)
    )
}
