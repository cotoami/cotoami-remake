package cotoami.subparts.pins

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Siblings
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
          group.eachWithOrderContext.map { case (ito, coto, order) =>
            sectionPinnedCoto(ito, coto, order)(sectionSubCotos)
          }
        )
      }: _*
    )
}
