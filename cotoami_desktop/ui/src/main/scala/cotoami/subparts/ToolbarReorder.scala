package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Link, OrderContext}
import cotoami.components.toolButton

object ToolbarReorder {

  def apply(
      link: Link,
      order: OrderContext
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "reorder-toolbar")(
      toolButton(
        classes = "move-to-top",
        symbol = "vertical_align_top",
        disabled = order.isFirst,
        onClick = e => {
          e.stopPropagation()
        }
      ),
      toolButton(
        classes = "move-up",
        symbol = "arrow_upward",
        disabled = order.isFirst,
        onClick = e => {
          e.stopPropagation()
        }
      ),
      toolButton(
        classes = "move-down",
        symbol = "arrow_downward",
        disabled = order.isLast,
        onClick = e => {
          e.stopPropagation()
        }
      ),
      toolButton(
        classes = "move-to-bottom",
        symbol = "vertical_align_bottom",
        disabled = order.isLast,
        onClick = e => {
          e.stopPropagation()
        }
      )
    )
}
