package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.components.{paneToggle, ToRight}

object PaneStock {

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "stock pane")(
      paneToggle("stock", dispatch, ToRight)
    )
}
