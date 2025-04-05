package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.components.{optionalClasses, SplitPane}
import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState

object AppBody {

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(id := "app-body", className := "body")(
      (model.uiState, model.repo.nodes.operating) match {
        case (Some(uiState), Some(_)) =>
          if (model.search.queryInput.isBlank())
            Some(defaultLayout(model, uiState))
          else
            Some(searchLayout(model, uiState))
        case _ => None
      }
    )

  private def defaultLayout(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Fragment(
      NavNodes(model, uiState),
      SplitPane(
        vertical = true,
        initialPrimarySize = uiState.paneSizes.getOrElse(
          NavCotonomas.PaneName,
          NavCotonomas.DefaultWidth
        ),
        resizable = uiState.paneOpened(NavCotonomas.PaneName),
        className = Some("main-split-pane default-layout"),
        onPrimarySizeChanged = Some((newSize) =>
          dispatch(AppMsg.ResizePane(NavCotonomas.PaneName, newSize))
        ),
        primary = SplitPane.Primary.Props(
          className = Some(
            optionalClasses(
              Seq(
                ("pane", true),
                ("folded", !uiState.paneOpened(NavCotonomas.PaneName))
              )
            )
          ),
          onClick = Option.when(!uiState.paneOpened(NavCotonomas.PaneName)) {
            () => dispatch(AppMsg.SetPaneOpen(NavCotonomas.PaneName, true))
          }
        )(
          paneToggle(NavCotonomas.PaneName),
          model.repo.nodes.current.map(
            NavCotonomas(model.navCotonomas, _)
          )
        ),
        secondary = SplitPane.Secondary.Props()(
          AppMain(model, uiState)
        )
      )
    )

  private def searchLayout(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Fragment(
      SplitPane(
        vertical = true,
        reverse = true,
        initialPrimarySize = uiState.paneSizes.getOrElse(
          PaneSearch.PaneName,
          PaneSearch.DefaultWidth
        ),
        className = Some("main-split-pane search-layout"),
        onPrimarySizeChanged = Some((newSize) =>
          dispatch(AppMsg.ResizePane(PaneSearch.PaneName, newSize))
        ),
        primary = SplitPane.Primary.Props()(
          PaneSearch(model.search)
        ),
        secondary = SplitPane.Secondary.Props()(
          AppMain(model, uiState)
        )
      )
    )
}
