package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg => AppMsg}
import cotoami.models.{Node, UiState}
import cotoami.repositories.Nodes
import cotoami.components.{materialSymbol, optionalClasses}

object NavNodes {
  final val PaneName = "nav-nodes"

  def apply(
      model: Model,
      uiState: UiState
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
    val nodes = model.domain.nodes
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
          dispatch(AppMsg.OpenOrClosePane(PaneName, true))
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
        data - "tooltip" := "All nodes",
        data - "placement" := "right",
        disabled := nodes.focused.isEmpty,
        onClick := (_ => dispatch(AppMsg.UnfocusNode))
      )(
        materialSymbol("stacks")
      ),
      div(className := "separator")(),
      button(
        className := "add default",
        data - "tooltip" := "Add node",
        data - "placement" := "right",
        onClick := (_ =>
          dispatch(Modal.Msg.OpenModal(Modal.Incorporate()).into)
        )
      )(
        materialSymbol("add")
      ),
      ul(className := "nodes")(
        nodes.operating.map(node =>
          li(className := "operating", key := node.id.uuid)(
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
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    (nodes.operatingRemote, nodes.operating, nodes.local) match {
      case (true, Some(operatingNode), Some(localNode)) =>
        Some(
          Fragment(
            button(
              className := "node default switch-back",
              data - "tooltip" := s"Back to ${localNode.name}",
              data - "placement" := "right",
              onClick := (_ =>
                dispatch(
                  Modal.Msg.OpenModal(
                    Modal.OperateAs(operatingNode, localNode)
                  ).into
                )
              )
            )(
              imgNode(localNode, "local"),
              imgNode(operatingNode, "operating")
            ),
            div(className := "separator")()
          )
        )
      case _ => None
    }

  private def buttonNode(
      node: Node,
      nodes: Nodes
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
    val status = nodes.parentStatus(node.id).flatMap(viewParentStatus(_))
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
      imgNode(node),
      status.map(s => span(className := s"status ${s.className}")(s.icon))
    )
  }
}
