package cotoami.subparts

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{DeselectNode, Model, Msg, SelectNode}
import cotoami.components.{
  material_symbol,
  node_img,
  optionalClasses,
  paneToggle
}
import cotoami.backend.Node

object NavNodes {
  val PaneName = "nav-nodes"

  def view(
      model: Model,
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
            ("selected", model.nodes.selected.isEmpty)
          )
        ),
        data - "tooltip" := "All nodes",
        data - "placement" := "right",
        onClick := ((e) => dispatch(DeselectNode))
      )(
        material_symbol("stacks")
      ),
      button(
        className := "add-node default",
        data - "tooltip" := "Add node",
        data - "placement" := "right"
      )(
        material_symbol("add")
      ),
      ul(className := "nodes")(
        model.nodes.local.map(node => li()(node_button(model, node, dispatch)))
      )
    )

  private def node_button(
      model: Model,
      node: Node,
      dispatch: Msg => Unit
  ): ReactElement =
    button(
      className := optionalClasses(
        Seq(
          ("node", true),
          ("default", true),
          ("selectable", true),
          ("selected", model.nodes.isSelecting(node))
        )
      ),
      disabled := model.nodes.isSelecting(node),
      data - "tooltip" := node.name,
      data - "placement" := "right",
      onClick := ((e) => dispatch(SelectNode(node.id)))
    )(node_img(node))
}
