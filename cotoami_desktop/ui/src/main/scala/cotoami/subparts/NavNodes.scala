package cotoami.subparts

import slinky.core._
import slinky.core.facade.{ReactElement, Fragment}
import slinky.hot
import slinky.web.html._

import cotoami.{Model, Msg, optionalClasses, paneToggle}

object NavNodes {
  val PaneName = "nav-nodes"

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    nav(
      className := optionalClasses(
        Seq(
          ("nodes", true),
          ("pane", true),
          ("folded", !model.uiState.paneOpened(PaneName))
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
        span(className := "material-symbols")("stacks")
      ),
      button(
        className := "add-node icon",
        data - "tooltip" := "Add node",
        data - "placement" := "right"
      )(
        span(className := "material-symbols")("add")
      ),
      ul(className := "nodes")
    )
}
