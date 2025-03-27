package cotoami.subparts

import scala.util.chaining._

import org.scalajs.dom
import org.scalajs.dom.HTMLElement
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.libs.tauri
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
            () => dispatch(AppMsg.SetPaneOpen(NavCotonomas.PaneName, true))
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
            dispatch(AppMsg.SetPaneOpen(PaneFlow.PaneName, true))
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
                ("map-opened", uiState.geomapOpened),
                (
                  "map-opened-vert",
                  uiState.geomapOpened && uiState.mapVertical
                ),
                (
                  "map-opened-hor",
                  uiState.geomapOpened && !uiState.mapVertical
                )
              )
            )
          ),
          onClick = Option.when(!stockOpened) { () =>
            dispatch(AppMsg.SetPaneOpen(PaneStock.PaneName, true))
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

  def resizeWindowOnPaneToggle(
      name: String,
      open: Boolean,
      uiState: UiState
  ): Cmd.One[Either[Throwable, Unit]] = {
    val flowWidth = uiState.paneSizes.getOrElse(
      PaneFlow.PaneName,
      PaneFlow.DefaultWidth
    )

    val stockPane =
      dom.document.getElementById("stock-pane") match {
        case element: HTMLElement => element
        case _                    => return Cmd.none
      }
    val stockWidth = stockPane.offsetWidth

    val foldedPaneWidth = 16

    (name, open) match {
      case (PaneFlow.PaneName, true) =>
        tauri.resizeWindow(flowWidth, 0).pipe(Cmd.fromFuture)
      case (PaneFlow.PaneName, false) =>
        tauri.resizeWindow(-1 * (flowWidth - foldedPaneWidth), 0)
          .pipe(Cmd.fromFuture)
      case (PaneStock.PaneName, true) =>
        tauri.resizeWindow(PaneStock.DefaultWidth, 0).pipe(Cmd.fromFuture)
      case (PaneStock.PaneName, false) =>
        tauri.resizeWindow(-1 * (stockWidth - foldedPaneWidth), 0)
          .pipe(Cmd.fromFuture)
      case _ => Cmd.none
    }
  }
}
