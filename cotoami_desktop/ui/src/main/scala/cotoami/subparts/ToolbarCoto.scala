package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Ito}
import cotoami.repository.Root
import cotoami.components.toolButton
import cotoami.subparts.Modal

object ToolbarCoto {

  def apply(
      coto: Coto
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    if (coto.isRepost) return None

    val buttons = Seq(
      Option.when(
        context.repo.cotos.anySelected &&
          !context.repo.cotos.isSelecting(coto.id)
      ) {
        toolButton(
          classes = "connect",
          symbol = Ito.ConnectIconName,
          tip = Some(context.i18n.text.Connect),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch(Modal.Msg.OpenModal(Modal.Connect(coto.id)))
          }
        )
      },
      Option.when(context.repo.canPin(coto.id)) {
        toolButton(
          classes = "pin-coto",
          symbol = "push_pin",
          tip = Some(context.i18n.text.Pin),
          tipPlacement = "left",
          disabled = context.repo.beingPinned(coto.id),
          onClick = e => {
            e.stopPropagation()
            dispatch(Root.Msg.Pin(coto.id))
          }
        )
      },
      Option.when(context.repo.nodes.canEdit(coto)) {
        toolButton(
          classes = "edit-coto",
          symbol = "edit",
          tip = Some(context.i18n.text.Edit),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch((Modal.Msg.OpenModal.apply _).tupled(Modal.EditCoto(coto)))
          }
        )
      },
      Option.when(context.repo.nodes.canCreateItosIn(coto.nodeId)) {
        toolButton(
          classes = "add-sub-coto",
          symbol = "add",
          tip = Some(context.i18n.text.WriteSubcoto),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch(
              Modal.Msg.OpenModal(Modal.Subcoto(coto.id, None, context.repo))
            )
          }
        )
      },
      Option.when(context.repo.canRepost(coto.id)) {
        toolButton(
          classes = "repost-coto",
          symbol = Coto.RepostIconName,
          tip = Some("Repost"),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            Modal.Repost(coto, context.repo) match {
              case Some(modal) => dispatch(Modal.Msg.OpenModal(modal))
              case None        => () // should be unreachable
            }
          }
        )
      },
      Option.when(context.repo.nodes.canPromote(coto)) {
        toolButton(
          classes = "promote-to-cotonoma",
          symbol = "drive_folder_upload",
          tip = Some("Promote to Cotonoma"),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch((Modal.Msg.OpenModal.apply _).tupled(Modal.Promote(coto)))
          }
        )
      },
      Option.when(context.repo.nodes.canEdit(coto) && !coto.isCotonoma) {
        toolButton(
          classes = "delete-coto",
          symbol = "delete",
          tip = Some("Delete"),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  "Are you sure you want to delete the coto?",
                  Root.Msg.DeleteCoto(coto.id)
                )
              )
            )
          }
        )
      },
      Some(
        if (context.repo.cotos.isSelecting(coto.id))
          toolButton(
            classes = "select-check-box",
            symbol = "check_box",
            tip = Some("Deselect"),
            tipPlacement = "left",
            onClick = e => {
              e.stopPropagation()
              dispatch(AppMsg.Deselect(coto.id))
            }
          )
        else
          toolButton(
            classes = "select-check-box",
            symbol = "check_box_outline_blank",
            tip = Some("Select"),
            tipPlacement = "left",
            onClick = e => {
              e.stopPropagation()
              dispatch(AppMsg.Select(coto.id))
            }
          )
      )
    ).flatten

    Option.when(!buttons.isEmpty) {
      section(className := "coto-toolbar")(buttons: _*)
    }
  }
}
