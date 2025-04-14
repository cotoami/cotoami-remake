package cotoami.subparts

import slinky.core.SyntheticEvent
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Node
import cotoami.subparts.ViewConnectionStatus

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

  def detailsConnectionStatus(status: ViewConnectionStatus): ReactElement =
    details(
      className := optionalClasses(
        Seq(
          ("node-status", true),
          (status.className, true),
          ("no-message", status.message.isEmpty)
        )
      )
    )(
      summary()(
        status.icon,
        span(className := "name")(status.title)
      ),
      status.message.map(p(className := "message")(_))
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

  def inputChildPrivileges(
      asOwner: Boolean,
      canEditItos: Boolean,
      disabled: Boolean,
      onAsOwnerChange: SyntheticEvent[_, _] => Unit,
      onCanEditItosChange: SyntheticEvent[_, _] => Unit
  )(implicit context: Context): ReactElement =
    Fragment(
      label(htmlFor := "as-owner")(
        input(
          `type` := "checkbox",
          id := "as-owner",
          checked := asOwner,
          slinky.web.html.disabled := disabled,
          onChange := onAsOwnerChange
        ),
        context.i18n.text.ChildPrivileges_asOwner
      ),
      label(htmlFor := "can-edit-itos")(
        input(
          `type` := "checkbox",
          id := "can-edit-itos",
          checked := canEditItos,
          slinky.web.html.disabled := disabled,
          onChange := onCanEditItosChange
        ),
        context.i18n.text.ChildPrivileges_canEditItos
      )
    )
}
