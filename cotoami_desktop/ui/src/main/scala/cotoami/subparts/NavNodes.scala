package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.backend.Node
import cotoami.repositories.Nodes
import cotoami.components.{materialSymbol, optionalClasses}

object NavNodes {
  final val PaneName = "nav-nodes"

  def apply(
      model: Model,
      uiState: UiState,
      dispatch: AppMsg => Unit
  ): ReactElement = {
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
      paneToggle(PaneName, dispatch),
      buttonSwitchBack(nodes, dispatch),
      button(
        className := optionalClasses(
          Seq(
            ("all", true),
            ("default", true),
            ("selectable", true),
            ("selected", nodes.selected.isEmpty)
          )
        ),
        data - "tooltip" := "All nodes",
        data - "placement" := "right",
        disabled := nodes.selected.isEmpty,
        onClick := (_ => dispatch(AppMsg.DeselectNode))
      )(
        materialSymbol("stacks")
      ),
      div(className := "separator")(),
      button(
        className := "add default",
        data - "tooltip" := "Add node",
        data - "placement" := "right",
        onClick := (_ =>
          dispatch(Modal.Msg.OpenModal(Modal.Incorporate()).toApp)
        )
      )(
        materialSymbol("add")
      ),
      ul(className := "nodes")(
        nodes.operating.map(node =>
          li(className := "operating", key := node.id.uuid)(
            buttonNode(node, nodes, dispatch)
          )
        ),
        nodes.parents.map(node =>
          li(className := "parent", key := node.id.uuid)(
            buttonNode(node, nodes, dispatch)
          )
        )
      )
    )
  }

  private def buttonSwitchBack(
      nodes: Nodes,
      dispatch: AppMsg => Unit
  ): ReactElement =
    (nodes.operatingRemote, nodes.operating, nodes.local) match {
      case (true, Some(operatingNode), Some(localNode)) =>
        Some(
          Fragment(
            button(
              className := "node default",
              data - "tooltip" := "Switch back to local",
              data - "placement" := "right",
              onClick := (_ =>
                dispatch(
                  Modal.Msg.OpenModal(
                    Modal.OperateAs(operatingNode, localNode)
                  ).toApp
                )
              )
            )(
              imgNode(localNode)
            ),
            div(className := "separator")()
          )
        )
      case _ => None
    }

  private def buttonNode(
      node: Node,
      nodes: Nodes,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val status = nodes.parentStatus(node.id).flatMap(parentStatusParts(_))
    val tooltip =
      status.map(s => s"${node.name} (${s.slug})").getOrElse(node.name)
    button(
      className := optionalClasses(
        Seq(
          ("node", true),
          ("default", true),
          ("selectable", true),
          ("selected", nodes.isSelecting(node.id))
        )
      ),
      disabled := nodes.isSelecting(node.id),
      data - "tooltip" := tooltip,
      data - "placement" := "right",
      disabled := nodes.isSelecting(node.id),
      onClick := (_ => dispatch(AppMsg.SelectNode(node.id)))
    )(
      imgNode(node),
      status.map(s => span(className := s"status ${s.slug}")(s.icon))
    )
  }
}
