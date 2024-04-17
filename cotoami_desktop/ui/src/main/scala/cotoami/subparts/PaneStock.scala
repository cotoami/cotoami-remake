package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.backend.{Coto, Link}
import cotoami.components.ToolButton

object PaneStock {
  val PaneName = "PaneStock"

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "stock")(
      section(className := "coto-catalog")(
        Option.when(!model.domain.pinnedCotos.isEmpty)(
          pinned(model.domain.pinnedCotos, dispatch)
        )
      )
    )

  def pinned(pinned: Seq[(Link, Coto)], dispatch: Msg => Unit): ReactElement =
    section(className := "pinned header-and-body")(
      header(className := "tools")(
        ToolButton(
          classes = "view-columns",
          tip = "Columns",
          symbol = "view_column"
        ),
        ToolButton(
          classes = "view-document selected",
          tip = "Document",
          symbol = "view_agenda"
        )
      )
    )
}
