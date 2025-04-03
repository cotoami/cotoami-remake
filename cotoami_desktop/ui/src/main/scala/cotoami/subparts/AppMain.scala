package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.libs.tauri
import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.components.{optionalClasses, SplitPane}

object AppMain {

  sealed trait Msg

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    (model, Cmd.none)

  def apply(
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
            paneToggle(PaneStock.PaneName)
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
    val foldedPaneWidth = 16
    val deltaWidth = (name, open) match {
      case (PaneFlow.PaneName, true) =>
        PaneFlow.widthIn(uiState) - foldedPaneWidth
      case (PaneFlow.PaneName, false) =>
        -1 * (PaneFlow.currentWidth - foldedPaneWidth)
      case (PaneStock.PaneName, true) =>
        PaneStock.DefaultWidth - foldedPaneWidth
      case (PaneStock.PaneName, false) =>
        -1 * (PaneStock.currentWidth - foldedPaneWidth)
      case _ => 0
    }
    if (deltaWidth != 0)
      tauri.resizeWindow(deltaWidth, 0).pipe(Cmd.fromFuture)
    else
      Cmd.none
  }
}
