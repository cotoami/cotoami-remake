package cotoami.subparts

import marubinotto.react.ReactNode
import marubinotto.react.html._

import marubinotto.components.toolButton
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Ito, OrderContext}
import cotoami.repository.Root

object ToolbarReorder {

  def apply(
      ito: Ito,
      order: OrderContext
  )(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactNode] =
    Option.when(context.repo.nodes.canEditItosIn(ito.nodeId)) {
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
