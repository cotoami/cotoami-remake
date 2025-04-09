package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.{materialSymbol, toolButton}

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
      div(className := "separator")(),
      toolButton(
        symbol = "add",
        tip = Some(context.i18n.text.NavNodes_addNode),
        tipPlacement = "right",
        classes = "add",
        onClick = _ => dispatch(Modal.Msg.OpenModal(Modal.Incorporate()))
      ),
      ul(className := "nodes")(
        nodes.operated.map(node =>
          li(className := "operated", key := node.id.uuid)(
            buttonNode(node, nodes)
          )
        ),
        nodes.parents.map(node =>
          li(className := "parent", key := node.id.uuid)(
            buttonNode(node, nodes)
          )
        )
      )
    )
  }

  private def buttonSwitchBack(
      nodes: Nodes
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    (nodes.operatingRemote, nodes.operated, nodes.local) match {
      case (true, Some(operatedNode), Some(localNode)) =>
        Some(
          Fragment(
            button(
              className := "node default switch-back",
              data - "tooltip" := s"Back to ${localNode.name}",
              data - "placement" := "right",
              onClick := (_ =>
                dispatch(
                  Modal.Msg.OpenModal(
                    Modal.OperateAs(operatedNode, localNode)
                  )
                )
              )
            )(
              PartsNode.imgNode(localNode, "local"),
              PartsNode.imgNode(operatedNode, "operated")
            ),
            div(className := "separator")()
          )
        )
      case _ => None
    }

  private def buttonNode(
      node: Node,
      nodes: Nodes
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    val status = nodes.parentStatus(node.id).flatMap(ViewParentStatus(_))
    val tooltip =
      status.map(s => s"${node.name} (${s.title})").getOrElse(node.name)
    button(
      className := optionalClasses(
        Seq(
          ("node", true),
          ("default", true),
          ("focusable", true),
          ("focused", nodes.isFocusing(node.id))
        )
      ),
      disabled := nodes.isFocusing(node.id),
      data - "tooltip" := tooltip,
      data - "placement" := "right",
      disabled := nodes.isFocusing(node.id),
      onClick := (_ => dispatch(AppMsg.FocusNode(node.id)))
    )(
      PartsNode.imgNode(node),
      status.map(s => span(className := s"status ${s.className}")(s.icon))
    )
  }
}
