package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.components.{optionalClasses, SplitPane}

object AppBody {

  def apply(
      model: Model
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    div(id := "app-body", className := "body")(
      (model.uiState, model.domain.nodes.operating) match {
        case (Some(uiState), Some(_)) =>
          Some(nodeContents(model, uiState))
        case _ => None
      }
    )

  private def nodeContents(
      model: Model,
      uiState: UiState
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
    implicit val _context: Context = model
    Fragment(
      NavNodes(model, uiState),
      SplitPane(
        vertical = true,
        initialPrimarySize = uiState.paneSizes.getOrElse(
          NavCotonomas.PaneName,
          NavCotonomas.DefaultWidth
        ),
        resizable = uiState.paneOpened(NavCotonomas.PaneName),
        className = Some("node-contents"),
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
          model.domain.nodes.current.map(
            NavCotonomas(model.navCotonomas, _)
          )
        ),
        secondary = SplitPane.Secondary.Props()(
          flowAndStock(model, uiState)
        )
      )
    )
  }

  private def flowAndStock(
      model: Model,
      uiState: UiState
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
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
            paneToggle(PaneFlow.PaneName)
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
                ("occupying", !flowOpened && stockOpened),
                ("map-opened", uiState.geomapOpened)
              )
            )
          ),
          onClick = Option.when(!stockOpened) { () =>
            dispatch(AppMsg.OpenOrClosePane(PaneStock.PaneName, true))
          }
        )(
          Option.when(flowOpened) {
            paneToggle(PaneStock.PaneName, ToRight)
          },
          PaneStock(model, uiState)
        )
      )
    )
  }
}
