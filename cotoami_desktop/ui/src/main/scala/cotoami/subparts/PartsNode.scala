package cotoami.subparts

import marubinotto.react.SyntheticEvent
import marubinotto.react.{Fragment, ReactNode}
import marubinotto.react.html._

import marubinotto.optionalClasses
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Node}
import cotoami.backend.ChildNodeInput
import cotoami.subparts.modeless.ModelessNodeProfile

object PartsNode {

  def imgNode(node: Node, additionalClasses: String = ""): ReactNode =
    img(
      className := s"node-icon ${additionalClasses}",
      alt := node.name,
      src := node.iconUrl
    )

  def spanNode(
      node: Node
  )(using dispatch: Into[AppMsg] => Unit): ReactNode =
    span(
      className := "node",
      onClick := (_ => dispatch(ModelessNodeProfile.Msg.Open(node.id)))
    )(
      imgNode(node),
      span(className := "name")(node.name)
    )

  def detailsConnectionStatus(status: ViewConnectionStatus): ReactNode =
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

  def buttonSwitchNode(switchTo: Node, tipPlacement: String = "bottom")(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactNode] = {
    val repo = context.repo
    Option.when(
      !repo.nodes.isSelfRemote &&
        repo.nodes.childPrivilegesTo(switchTo.id)
          .map(_.asOwner).getOrElse(false)
    ) {
      toolButton(
        symbol = Node.SwitchIconName,
        tip = Some(context.i18n.text.ModalSwitchNode_title),
        tipPlacement = tipPlacement,
        classes = "switch-node",
        onClick = _ =>
          dispatch(
            Modal.Msg.OpenModal(
              Modal.SwitchNode(repo.nodes.self.get, switchTo)
            )
          )
      )
    }
  }

  def childPrivileges(child: Option[ChildNode])(using
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
      onAsOwnerChange: SyntheticEvent[?, ?] => Unit,
      onCanEditItosChange: SyntheticEvent[?, ?] => Unit,
      onCanPostCotonomas: SyntheticEvent[?, ?] => Unit
  )(using context: Context): ReactNode =
    Fragment(
      label(htmlFor := "as-owner")(
        input(
          `type` := "checkbox",
          id := "as-owner",
          checked := values.asOwner,
          marubinotto.react.html.disabled := disabled,
          onChange := onAsOwnerChange
        ),
        context.i18n.text.ChildPrivileges_asOwner
      ),
      label(htmlFor := "can-post-cotos")(
        input(
          `type` := "checkbox",
          id := "can-post-cotos",
          checked := true,
          marubinotto.react.html.disabled := true
        ),
        context.i18n.text.ChildPrivileges_canPostCotos
      ),
      label(htmlFor := "can-edit-itos")(
        input(
          `type` := "checkbox",
          id := "can-edit-itos",
          checked := values.canEditItos,
          marubinotto.react.html.disabled := disabled,
          onChange := onCanEditItosChange
        ),
        context.i18n.text.ChildPrivileges_canEditItos
      ),
      label(htmlFor := "can-post-cotonomas")(
        input(
          `type` := "checkbox",
          id := "can-post-cotonomas",
          checked := values.canPostCotonomas,
          marubinotto.react.html.disabled := disabled,
          onChange := onCanPostCotonomas
        ),
        context.i18n.text.ChildPrivileges_canPostCotonomas
      )
    )
}
