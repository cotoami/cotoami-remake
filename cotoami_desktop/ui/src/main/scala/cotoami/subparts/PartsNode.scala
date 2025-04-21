package cotoami.subparts

import slinky.core.SyntheticEvent
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Node}
import cotoami.backend.ChildNodeInput
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
      !repo.nodes.isSelfRemote &&
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
              Modal.OperateAs(repo.nodes.self.get, switchTo)
            )
          )
      )
    }
  }

  def childPrivileges(child: Option[ChildNode])(implicit
      context: Context
  ): Seq[String] =
    child.map { child =>
      if (child.asOwner)
        Seq(context.i18n.text.Owner)
      else
        Seq(
          Some(context.i18n.text.ChildPrivileges_canPostCotos),
          Option.when(child.canPostCotonomas)(
            context.i18n.text.ChildPrivileges_canPostCotonomas
          ),
          Option.when(child.canEditItos)(
            context.i18n.text.ChildPrivileges_canEditItos
          )
        ).flatten
    }.getOrElse(Seq(context.i18n.text.ChildPrivileges_readOnly))

  def inputChildPrivileges(
      values: ChildNodeInput,
      disabled: Boolean,
      onAsOwnerChange: SyntheticEvent[_, _] => Unit,
      onCanEditItosChange: SyntheticEvent[_, _] => Unit,
      onCanPostCotonomas: SyntheticEvent[_, _] => Unit
  )(implicit context: Context): ReactElement =
    Fragment(
      label(htmlFor := "as-owner")(
        input(
          `type` := "checkbox",
          id := "as-owner",
          checked := values.asOwner,
          slinky.web.html.disabled := disabled,
          onChange := onAsOwnerChange
        ),
        context.i18n.text.ChildPrivileges_asOwner
      ),
      label(htmlFor := "can-post-cotos")(
        input(
          `type` := "checkbox",
          id := "can-post-cotos",
          checked := true,
          slinky.web.html.disabled := true
        ),
        context.i18n.text.ChildPrivileges_canPostCotos
      ),
      label(htmlFor := "can-edit-itos")(
        input(
          `type` := "checkbox",
          id := "can-edit-itos",
          checked := values.canEditItos,
          slinky.web.html.disabled := disabled,
          onChange := onCanEditItosChange
        ),
        context.i18n.text.ChildPrivileges_canEditItos
      ),
      label(htmlFor := "can-post-cotonomas")(
        input(
          `type` := "checkbox",
          id := "can-post-cotonomas",
          checked := values.canPostCotonomas,
          slinky.web.html.disabled := disabled,
          onChange := onCanPostCotonomas
        ),
        context.i18n.text.ChildPrivileges_canPostCotonomas
      )
    )
}
