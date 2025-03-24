package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.components.{optionalClasses, SplitPane}

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
            () => dispatch(AppMsg.OpenOrClosePane(NavCotonomas.PaneName, true))
          }
        )(
          paneToggle(NavCotonomas.PaneName),
          model.repo.nodes.current.map(
            NavCotonomas(model.navCotonomas, _)
          )
        ),
        secondary = SplitPane.Secondary.Props()(
          flowAndStock(model, uiState)
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
          flowAndStock(model, uiState)
        )
      )
    )

  private def flowAndStock(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val flowOpened = uiState.paneOpened(PaneFlow.PaneName)
    val stockOpened = uiState.paneOpened(PaneStock.PaneName)
    slinky.web.html.main(className := "fill")(
      SplitPane(
        vertical = true,
        reverse = uiState.reverseMainPanes,
        initialPrimarySize = uiState.paneSizes.getOrElse(
          PaneFlow.PaneName,
          PaneFlow.DefaultWidth
        ),
        resizable = flowOpened && stockOpened,
        className = Some("main"),
        onPrimarySizeChanged = Some((newSize) =>
          dispatch(AppMsg.ResizePane(PaneFlow.PaneName, newSize))
        ),
        primary = SplitPane.Primary.Props(
          className = Some(
            optionalClasses(
              Seq(
                ("flow", true),
                ("pane", true),
                ("folded", !flowOpened),
                ("occupying", flowOpened && !stockOpened)
              )
            )
          ),
          onClick = Option.when(!flowOpened) { () =>
            dispatch(AppMsg.OpenOrClosePane(PaneFlow.PaneName, true))
          }
        )(
          Option.when(stockOpened) {
            paneToggle(
              PaneFlow.PaneName,
              if (uiState.reverseMainPanes)
                ToRight
              else
                ToLeft
            )
          },
          PaneFlow(model, uiState)
        ),
        secondary = SplitPane.Secondary.Props(
          className = Some(
            optionalClasses(
              Seq(
                ("stock", true),
                ("pane", true),
                ("folded", !stockOpened),
                ("map-opened", uiState.geomapOpened)
              )
            )
          ),
          onClick = Option.when(!stockOpened) { () =>
            dispatch(AppMsg.OpenOrClosePane(PaneStock.PaneName, true))
          }
        )(
          Option.when(flowOpened) {
            paneToggle(
              PaneStock.PaneName,
              if (uiState.reverseMainPanes)
                ToLeft
              else
                ToRight
            )
          },
          PaneStock(model, uiState)
        )
      )
    )
  }
}
