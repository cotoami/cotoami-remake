package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.Context
import cotoami.models.Coto
import cotoami.components.toolButton

object ToolbarCoto {

  def apply(coto: Coto)(implicit context: Context): ReactElement =
    section(className := "coto-toolbar")(
      Option.when(context.domain.canEditLinks) {
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
          classes = "edit-coto"
        )
      },
      Option.when(context.domain.nodes.canEditLinksIn(coto.nodeId)) {
        toolButton(
          symbol = "add",
          tip = "Write a sub-coto",
          tipPlacement = "left",
          classes = "add-sub-coto"
        )
      },
      toolButton(
        symbol = "repeat",
        tip = "Repost",
        tipPlacement = "left",
        classes = "repost-coto"
      ),
      Option.when(context.domain.nodes.canEdit(coto) && !coto.isCotonoma) {
        toolButton(
          symbol = "drive_folder_upload",
          tip = "Promote to a cotonoma",
          tipPlacement = "left",
          classes = "promote-to-cotonoma"
        )
      },
      Option.when(context.domain.canDelete(coto)) {
        toolButton(
          symbol = "delete",
          tip = "Delete",
          tipPlacement = "left",
          classes = "delete-coto"
        )
      }
    )
}
