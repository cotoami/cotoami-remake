package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Ito, OrderContext}
import cotoami.repository.Root
import cotoami.components.toolButton

object ToolbarReorder {

  def apply(
      ito: Ito,
      order: OrderContext
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(context.repo.nodes.canCreateItosIn(ito.nodeId)) {
      val reordering = context.repo.reordering.contains(ito.sourceCotoId)
      section(className := "reorder-toolbar")(
        toolButton(
          classes = "move-to-top",
          symbol = "vertical_align_top",
          disabled = order.isFirst || reordering,
          onClick = e => {
            e.stopPropagation()
            dispatch(Root.Msg.ChangeOrder(ito, 1))
          }
        ),
        toolButton(
          classes = "move-up",
          symbol = "arrow_upward",
          disabled = order.isFirst || reordering,
          onClick = e => {
            e.stopPropagation()
            order.previous.map(previous =>
              dispatch(Root.Msg.ChangeOrder(ito, previous))
            )
          }
        ),
        toolButton(
          classes = "move-down",
          symbol = "arrow_downward",
          disabled = order.isLast || reordering,
          onClick = e => {
            e.stopPropagation()
            order.next.map(next =>
              dispatch(Root.Msg.ChangeOrder(ito, next + 1))
            )
          }
        ),
        toolButton(
          classes = "move-to-bottom",
          symbol = "vertical_align_bottom",
          disabled = order.isLast || reordering,
          onClick = e => {
            e.stopPropagation()
            dispatch(Root.Msg.ChangeOrder(ito, order.max + 1))
          }
        ),
        Option.when(reordering) {
          span(className := "reordering", aria - "busy" := "true")()
        }
      )
    }
}
