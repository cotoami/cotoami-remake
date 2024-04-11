package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.components.{optionalClasses, paneToggle, ToRight}

object PaneStock {
  val PaneName = "PaneStock"

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("stock", true),
          ("pane", true),
          ("folded", !uiState.paneOpened(PaneName))
        )
      )
    )(
      paneToggle(PaneName, dispatch, ToRight)
    )
}
