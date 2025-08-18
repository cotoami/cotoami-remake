package cotoami.subparts.pins

import scala.scalajs.js.JSConverters._

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.MasonicMasonry
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Ito, OrderContext, Siblings}
import cotoami.subparts.PartsNode

object MasonryLayout {

  def apply(
      pins: Siblings
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "pinned-cotos siblings")(
      pins.groupsInOrder.map { group =>
        section(
          className := optionalClasses(
            Seq(
              ("sibling-group", true),
              ("in-other-nodes", !group.isMain)
            )
          )
        )(
          Option.when(!group.isMain) {
            context.repo.nodes.get(group.nodeId).map { node =>
              div(className := "ito-node")(PartsNode.spanNode(node))
            }
          },
          MasonicMasonry(
            items = group.eachWithOrderContext.map { case (ito, coto, order) =>
              PinnedCoto(ito, coto, order).asInstanceOf[scala.Any]
            }.toSeq.toJSArray,
            render = props => {
              val data = props.data.asInstanceOf[PinnedCoto]
              sectionPinnedCoto(data.ito, data.coto, data.order)(
                sectionSubCotos
              )
            },
            columnWidth = Some(300),
            columnGutter = Some(16),
            rowGutter = Some(20)
          )
        )
      }: _*
    )

  case class PinnedCoto(
      ito: Ito,
      coto: Coto,
      order: OrderContext
  )
}
