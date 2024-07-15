package cotoami.subparts

import slinky.core.facade.ReactElement

import cotoami.{Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.components.{optionalClasses, SplitPane}

object AppBody {

  def contents(
      model: Model,
      uiState: UiState,
      dispatch: AppMsg => Unit
  ): Seq[ReactElement] = Seq(
    NavNodes(model, uiState, dispatch),
    SplitPane(
      vertical = true,
      initialPrimarySize = uiState.paneSizes.getOrElse(
        NavCotonomas.PaneName,
        NavCotonomas.DefaultWidth
      ),
      resizable = uiState.paneOpened(NavCotonomas.PaneName),
      className = Some("node-contents"),
      onResizeStart = None,
      onResizeEnd = None,
      onPrimarySizeChanged = Some((newSize) =>
        dispatch(AppMsg.ResizePane(NavCotonomas.PaneName, newSize))
      )
    )(
      SplitPane.Primary(
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
        paneToggle(NavCotonomas.PaneName, dispatch),
        model.domain.nodes.current.map(
          NavCotonomas(model.navCotonomas, _, dispatch)(model)
        )
      ),
      SplitPane.Secondary(className = None, onClick = None)(
        flowAndStock(model, uiState, dispatch)
      )
    )
  )

  private def flowAndStock(
      model: Model,
      uiState: UiState,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val flowOpened = uiState.paneOpened(PaneFlow.PaneName)
    val stockOpened = uiState.paneOpened(PaneStock.PaneName)
    slinky.web.html.main()(
      SplitPane(
        vertical = true,
        initialPrimarySize = uiState.paneSizes.getOrElse(
          PaneFlow.PaneName,
          PaneFlow.DefaultWidth
        ),
        resizable = flowOpened && stockOpened,
        className = Some("main"),
        onResizeStart = None,
        onResizeEnd = None,
        onPrimarySizeChanged = Some((newSize) =>
          dispatch(AppMsg.ResizePane(PaneFlow.PaneName, newSize))
        )
      )(
        SplitPane.Primary(
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
            paneToggle(PaneFlow.PaneName, dispatch)
          },
          PaneFlow(model, uiState, dispatch)
        ),
        SplitPane.Secondary(
          className = Some(
            optionalClasses(
              Seq(
                ("stock", true),
                ("pane", true),
                ("folded", !stockOpened),
                ("occupying", !flowOpened && stockOpened)
              )
            )
          ),
          onClick = Option.when(!stockOpened) { () =>
            dispatch(AppMsg.OpenOrClosePane(PaneStock.PaneName, true))
          }
        )(
          Option.when(flowOpened) {
            paneToggle(PaneStock.PaneName, dispatch, ToRight)
          },
          PaneStock(model, uiState, dispatch)
        )
      )
    )
  }
}
