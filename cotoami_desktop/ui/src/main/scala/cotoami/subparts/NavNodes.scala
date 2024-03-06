package cotoami.subparts

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.components.{
  optionalClasses,
  paneToggle,
  material_symbol,
  node_img
}

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
            ("icon", true),
            ("selectable", true),
            ("selected", model.selectedNodeId.isEmpty)
          )
        ),
        data - "tooltip" := "All nodes",
        data - "placement" := "right"
      )(
        material_symbol("stacks")
      ),
      button(
        className := "add-node icon",
        data - "tooltip" := "Add node",
        data - "placement" := "right"
      )(
        material_symbol("add")
      ),
      ul(className := "nodes")(
        model.localNode.map(node =>
          li()(
            button(
              className := optionalClasses(
                Seq(
                  ("node", true),
                  ("icon", true),
                  ("selectable", true),
                  ("selected", model.selected(node))
                )
              ),
              data - "tooltip" := node.name,
              data - "placement" := "right"
            )(node_img(node))
          )
        )
      )
    )
}
