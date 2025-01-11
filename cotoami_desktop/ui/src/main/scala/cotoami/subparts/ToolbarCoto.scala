package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Coto
import cotoami.repositories.Domain
import cotoami.components.toolButton
import cotoami.subparts.Modal

object ToolbarCoto {

  def apply(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "coto-toolbar")(
      Option.when(context.domain.canPin(coto.id)) {
        toolButton(
          symbol = "push_pin",
          tip = "Pin",
          tipPlacement = "left",
          classes = "pin-coto"
        )
      },
      Option.when(context.domain.nodes.canEdit(coto)) {
        toolButton(
          symbol = "edit",
          tip = "Edit",
          tipPlacement = "left",
          classes = "edit-coto",
          onClick = e => {
            e.stopPropagation()
            dispatch(
              (Modal.Msg.OpenModal.apply _).tupled(
                Modal.CotoEditor(coto)
              )
            )
          }
        )
      },
      Option.when(context.domain.nodes.canEditLinksIn(coto.nodeId)) {
        toolButton(
          symbol = "add",
          tip = "Write a linked coto",
          tipPlacement = "left",
          classes = "add-linked-coto"
        )
      },
      Option.when(context.domain.canRepost(coto.id)) {
        toolButton(
          symbol = "repeat",
          tip = "Repost",
          tipPlacement = "left",
          classes = "repost-coto",
          onClick = e => {
            e.stopPropagation()
            Modal.Repost(coto, context.domain) match {
              case Some(modal) => dispatch(Modal.Msg.OpenModal(modal))
              case None        => () // should be unreachable
            }
          }
        )
      },
      Option.when(context.domain.nodes.canPromote(coto)) {
        toolButton(
          symbol = "drive_folder_upload",
          tip = "Promote to a cotonoma",
          tipPlacement = "left",
          classes = "promote-to-cotonoma"
        )
      },
      Option.when(context.domain.nodes.canEdit(coto) && !coto.isCotonoma) {
        toolButton(
          symbol = "delete",
          tip = "Delete",
          tipPlacement = "left",
          classes = "delete-coto",
          onClick = e => {
            e.stopPropagation()
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  "Are you sure you want to delete the coto?",
                  Domain.Msg.DeleteCoto(coto.id)
                )
              )
            )
          }
        )
      }
    )
}
