package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.{materialSymbol, materialSymbolFilled, toolButton}

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Node, UiState}
import cotoami.repository.Nodes

object NavNodes {
  final val PaneName = "nav-nodes"

  def apply(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val nodes = model.repo.nodes
    nav(
      className := optionalClasses(
        Seq(
          ("nodes", true),
          ("pane", true),
          ("folded", !uiState.paneOpened(PaneName))
        )
      ),
      aria - "label" := "Nodes",
      onClick := (_ =>
        if (!uiState.paneOpened(PaneName)) {
          dispatch(AppMsg.SetPaneOpen(PaneName, true))
        }
      )
    )(
      paneToggle(PaneName),
      buttonSwitchBack(nodes),
      div(
        className := optionalClasses(
          Seq(
            ("button-all", true),
            ("read-trackable", true),
            ("focused", nodes.focused.isEmpty)
          )
        )
      )(
        button(
          className := optionalClasses(
            Seq(
              ("all", true),
              ("default", true),
              ("focusable", true),
              ("focused", nodes.focused.isEmpty)
            )
          ),
          data - "tooltip" := context.i18n.text.NavNodes_allNodes,
          data - "placement" := "right",
          disabled := nodes.focused.isEmpty,
          onClick := (_ => dispatch(AppMsg.UnfocusNode))
        )(
          materialSymbol("stacks")
        ),
        Option.when(nodes.anyUnreadPosts)(
          materialSymbolFilled("brightness_1", "unread-mark")
        )
      ),
      div(className := "separator")(),
      toolButton(
        symbol = "add",
        tip = Some(context.i18n.text.NavNodes_addNode),
        tipPlacement = "right",
        classes = "add",
        onClick = _ => dispatch(Modal.Msg.OpenModal(Modal.Incorporate()))
      ),
      ul(className := "nodes")(
        nodes.self.map(node =>
          li(className := "self", key := node.id.uuid)(
            buttonNode(node, nodes.anyUnreadPostsInSelf, nodes)
          )
        ),
        nodes.parentNodes.map(node =>
          li(className := "parent", key := node.id.uuid)(
            buttonNode(node, nodes.parents.anyUnreadPostsIn(node.id), nodes)
          )
        )
      )
    )
  }

  private def buttonSwitchBack(
      nodes: Nodes
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    (nodes.isSelfRemote, nodes.self, nodes.local) match {
      case (true, Some(selfNode), Some(localNode)) =>
        Some(
          Fragment(
            button(
              className := "node default switch-back",
              data - "tooltip" := s"Back to ${localNode.name}",
              data - "placement" := "right",
              onClick := (_ =>
                dispatch(
                  Modal.Msg.OpenModal(
                    Modal.SwitchNode(selfNode, localNode)
                  )
                )
              )
            )(
              PartsNode.imgNode(localNode, "local"),
              PartsNode.imgNode(selfNode, "self")
            ),
            div(className := "separator")()
          )
        )
      case _ => None
    }

  private def buttonNode(
      node: Node,
      anyUnreadPosts: Boolean,
      nodes: Nodes
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val status = nodes.parentStatus(node.id)
      .flatMap(ViewConnectionStatus(_).onlyIfNotConnected)
    val tooltip =
      status.map(s => s"${node.name} (${s.title})").getOrElse(node.name)
    val focused = nodes.isFocusing(node.id)
    div(
      className := optionalClasses(
        Seq(
          ("button-node", true),
          ("read-trackable", true),
          ("focused", focused)
        )
      )
    )(
      button(
        className := optionalClasses(
          Seq(
            ("node", true),
            ("default", true),
            ("focusable", true),
            ("focused", focused)
          )
        ),
        disabled := focused,
        data - "tooltip" := tooltip,
        data - "placement" := "right",
        onClick := (_ => dispatch(AppMsg.FocusNode(node.id)))
      )(
        PartsNode.imgNode(node),
        status.map(s => span(className := s"status ${s.className}")(s.icon))
      ),
      Option.when(anyUnreadPosts)(
        materialSymbolFilled("brightness_1", "unread-mark")
      )
    )
  }
}
