package cotoami.subparts.pins

import scala.scalajs.js.JSConverters._

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.MasonicMasonry
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Ito, OrderContext, Siblings}
import cotoami.subparts.{PartsNode, SectionPins}

object MasonryLayout {

  val MinColumnWidth = 100
  val MaxColumnWidth = 1000

  def apply(
      pins: Siblings,
      cotonomaId: Id[Cotonoma],
      columnWidth: Int
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "pinned-cotos siblings")(
      div(className := "column-width-slider")(
        input(
          `type` := "range",
          min := MinColumnWidth.toString(),
          max := MaxColumnWidth.toString(),
          value := columnWidth.toString(),
          onChange := (e => {
            e.target.value.toIntOption.foreach(width =>
              dispatch(SectionPins.Msg.SetMasonryColumnWidth(cotonomaId, width))
            )
          })
        )
      ),
      div(className := "pinned-sibling-groups")(
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
              items = group.eachWithOrderContext.map {
                case (ito, coto, order) =>
                  PinnedCoto(ito, coto, order).asInstanceOf[scala.Any]
              }.toSeq.toJSArray,
              itemKey =
                (item, index) => item.asInstanceOf[PinnedCoto].ito.id.uuid,
              render = props => {
                val data = props.data.asInstanceOf[PinnedCoto]
                sectionPinnedCoto(data.ito, data.coto, data.order)(
                  sectionSubCotos
                )
              },
              columnWidth = columnWidth,
              columnGutter = Some(16),
              rowGutter = Some(20),
              // Disable the virtualization to ensure to display all the cotos
              // https://github.com/jaredLunde/masonic/issues/120
              overscanBy = Double.PositiveInfinity
            )
          )
        }: _*
      )
    )

  case class PinnedCoto(
      ito: Ito,
      coto: Coto,
      order: OrderContext
  )
}
