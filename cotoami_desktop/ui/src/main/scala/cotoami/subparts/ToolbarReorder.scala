package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Link
import cotoami.components.toolButton

object ToolbarReorder {

  def apply(
      link: Link,
      minOrder: Int,
      maxOrder: Int,
      previousOrder: Option[Int],
      nextOrder: Option[Int]
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "reorder-toolbar")(
      toolButton(
        classes = "move-to-top",
        symbol = "skip_previous",
        tip = Some("To top"),
        tipPlacement = "right",
        disabled = link.order == minOrder,
        onClick = e => {
          e.stopPropagation()
        }
      ),
      toolButton(
        classes = "move-up",
        symbol = "play_arrow",
        tip = Some("Up"),
        tipPlacement = "right",
        disabled = link.order == minOrder,
        onClick = e => {
          e.stopPropagation()
        }
      ),
      toolButton(
        classes = "move-down",
        symbol = "play_arrow",
        tip = Some("Down"),
        tipPlacement = "right",
        disabled = link.order == maxOrder,
        onClick = e => {
          e.stopPropagation()
        }
      ),
      toolButton(
        classes = "move-to-bottom",
        symbol = "skip_next",
        tip = Some("To bottom"),
        tipPlacement = "right",
        disabled = link.order == maxOrder,
        onClick = e => {
          e.stopPropagation()
        }
      )
    )
}
