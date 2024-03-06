package cotoami.subparts

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg, optionalClasses, paneToggle, icon}

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
        className := "all-nodes icon selectable selected",
        data - "tooltip" := "All nodes",
        data - "placement" := "right"
      )(
        icon("stacks")
      ),
      button(
        className := "add-node icon",
        data - "tooltip" := "Add node",
        data - "placement" := "right"
      )(
        icon("add")
      ),
      ul(className := "nodes")(
      )
    )
}
