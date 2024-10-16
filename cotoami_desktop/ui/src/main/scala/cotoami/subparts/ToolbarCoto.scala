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
      toolButton(
        symbol = "repeat",
        tip = "Repost",
        tipPlacement = "left",
        classes = "repost-coto"
      )
    )
}
