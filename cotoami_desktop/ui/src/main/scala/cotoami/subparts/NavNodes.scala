package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{DeselectNode, Model, Msg, OpenOrClosePane, SelectNode}
import cotoami.models.UiState
import cotoami.backend.{Node, NotConnected}
import cotoami.repositories.Nodes
import cotoami.components.{materialSymbol, optionalClasses}

object NavNodes {
  final val PaneName = "nav-nodes"

  def apply(
      model: Model,
      uiState: UiState,
      dispatch: Msg => Unit
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
          dispatch(OpenOrClosePane(PaneName, true))
        }
      )
    )(
      paneToggle(PaneName, dispatch),
      button(
        className := optionalClasses(
          Seq(
            ("all-nodes", true),
            ("default", true),
            ("selectable", true),
            ("selected", nodes.selected.isEmpty)
          )
        ),
        data - "tooltip" := "All nodes",
        data - "placement" := "right",
        disabled := nodes.selected.isEmpty,
        onClick := (_ => dispatch(DeselectNode))
      )(
        materialSymbol("stacks")
      ),
      button(
        className := "add-node default",
        data - "tooltip" := "Add node",
        data - "placement" := "right",
        onClick := (_ => dispatch(Modal.OpenModal(Modal.AddNode()).asAppMsg))
      )(
        materialSymbol("add")
      ),
      ul(className := "nodes")(
        nodes.local.map(node =>
          li(className := "local")(nodeButton(node, nodes, dispatch))
        ),
        nodes.parents.map(node =>
          li(className := "parent")(nodeButton(node, nodes, dispatch))
        )
      )
    )
  }

  private def nodeButton(
      node: Node,
      nodes: Nodes,
      dispatch: Msg => Unit
  ): ReactElement = {
    val status = nodeStatus(node, nodes)
    val tooltip =
      status.map(s => s"${node.name} (${s._1})").getOrElse(node.name)
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
      onClick := ((e) => dispatch(SelectNode(node.id)))
    )(
      nodeImg(node),
      status.map(_._2)
    )
  }

  private def nodeStatus(
      node: Node,
      nodes: Nodes
  ): Option[(String, ReactElement)] =
    nodes.getServer(node.id).flatMap(_.notConnected.map {
      case NotConnected.Disabled =>
        ("disabled", statusIcon("", "sync_disabled"))
      case NotConnected.Connecting(details) =>
        (
          "connecting",
          span(className := "status busy", aria - "busy" := "true")()
        )
      case NotConnected.InitFailed(details) =>
        ("error", statusIcon("error", "error"))
      case NotConnected.Disconnected(details) =>
        ("disconnected", statusIcon("", "do_not_disturb_on"))
    })

  private def statusIcon(status: String, icon: String): ReactElement =
    span(className := s"status ${status}")(materialSymbol(icon))
}
