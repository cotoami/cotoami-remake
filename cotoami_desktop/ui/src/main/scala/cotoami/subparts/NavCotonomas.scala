package cotoami.subparts

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.components.{
  material_symbol,
  optionalClasses,
  paneToggle,
  SplitPane
}
import cotoami.backend.Node

object NavCotonomas {
  val PaneName = "nav-cotonomas"
  val DefaultWidth = 230

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    SplitPane.Primary(className =
      Some(
        optionalClasses(
          Seq(
            ("pane", true),
            ("folded", !uiState.paneOpened(PaneName))
          )
        )
      )
    )(
      paneToggle(PaneName, dispatch),
      model.currentNode().map(cotonomasNav(model, _, dispatch))
    )

  def cotonomasNav(
      model: Model,
      currentNode: Node,
      dispatch: Msg => Unit
  ): ReactElement =
    nav(className := "cotonomas header-and-body")(
      header()(
        a(className := "cotonoma home", title := s"${currentNode.name} home")(
          material_symbol("home"),
          currentNode.name
        )
      )
    )
}
