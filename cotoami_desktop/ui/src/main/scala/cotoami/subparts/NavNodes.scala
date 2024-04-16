package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{DeselectNode, Model, Msg, SelectNode}
import cotoami.components.{materialSymbol, optionalClasses, paneToggle}
import cotoami.backend.Node
import cotoami.repositories.Nodes

object NavNodes {
  val PaneName = "nav-nodes"

  def view(
      nodes: Nodes,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    nav(
      className := optionalClasses(
        Seq(
          ("nodes", true),
          ("pane", true),
          ("folded", !uiState.paneOpened(PaneName))
        )
      ),
      aria - "label" := "Nodes"
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
        onClick := ((e) => dispatch(DeselectNode))
      )(
        materialSymbol("stacks")
      ),
      button(
        className := "add-node default",
        data - "tooltip" := "Add node",
        data - "placement" := "right"
      )(
        materialSymbol("add")
      ),
      ul(className := "nodes")(
        nodes.local.map(node => li()(nodeButton(nodes, node, dispatch)))
      )
    )

  private def nodeButton(
      nodes: Nodes,
      node: Node,
      dispatch: Msg => Unit
  ): ReactElement =
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
      data - "tooltip" := node.name,
      data - "placement" := "right",
      disabled := nodes.isSelecting(node.id),
      onClick := ((e) => dispatch(SelectNode(node.id)))
    )(nodeImg(node))
}
