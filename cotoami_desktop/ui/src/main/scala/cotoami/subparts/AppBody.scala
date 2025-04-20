package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.SplitPane

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState

object AppBody {

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(
      id := "app-body",
      className := optionalClasses(
        Seq(
          ("body", true),
          ("search-active", model.search.active)
        )
      )
    )(
      (model.uiState, model.repo.nodes.self) match {
        case (Some(uiState), Some(_)) => Some(content(model, uiState))
        case _                        => None
      }
    )

  private def content(
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
        className = Some("main-split-pane"),
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
            NavCotonomas(model.navCotonomas, model.nodeTools)
          )
        ),
        secondary = SplitPane.Secondary.Props()(
          AppMain(model, uiState)
        )
      ),
      Option.when(model.search.active) {
        PaneSearch(model.search)
      }
    )
}
