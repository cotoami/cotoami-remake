package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.models.Coto
import cotoami.components.toolButton

object ToolbarCoto {

  def apply(coto: Coto): ReactElement =
    section(className := "coto-toolbar")(
      toolButton(
        symbol = "push_pin",
        tip = "Pin",
        tipPlacement = "left",
        classes = "pin-coto"
      ),
      toolButton(
        symbol = "edit",
        tip = "Edit",
        tipPlacement = "left",
        classes = "edit-coto"
      ),
      toolButton(
        symbol = "repeat",
        tip = "Repost",
        tipPlacement = "left",
        classes = "repost-coto"
      )
    )
}
