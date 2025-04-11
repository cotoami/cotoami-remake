package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.toolButton
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Node

object PartsNode {

  def imgNode(node: Node, additionalClasses: String = ""): ReactElement =
    img(
      className := s"node-icon ${additionalClasses}",
      alt := node.name,
      src := node.iconUrl
    )

  def spanNode(
      node: Node
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    span(
      className := "node",
      onClick := (_ =>
        dispatch(
          (Modal.Msg.OpenModal.apply _).tupled(
            Modal.NodeProfile(node.id)
          )
        )
      )
    )(
      imgNode(node),
      span(className := "name")(node.name)
    )

  def buttonOperateAs(switchTo: Node, tipPlacement: String = "bottom")(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    val repo = context.repo
    Option.when(
      !repo.nodes.operatingRemote &&
        repo.nodes.childPrivilegesTo(switchTo.id)
          .map(_.asOwner).getOrElse(false)
    ) {
      toolButton(
        symbol = Node.SwitchIconName,
        tip = Some("Operate as"),
        tipPlacement = tipPlacement,
        classes = "operate",
        onClick = _ =>
          dispatch(
            Modal.Msg.OpenModal(
              Modal.OperateAs(repo.nodes.operated.get, switchTo)
            )
          )
      )
    }
  }

}
