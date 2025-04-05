package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.Action
import marubinotto.fui.Cmd
import marubinotto.libs.tauri
import marubinotto.components.{paneToggle, SplitPane}

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState

object AppMain {

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.AppMainMsg(this)
  }

  object Msg {
    case class SetPaneFlowOpen(open: Boolean) extends Msg
    case class SetPaneStockOpen(open: Boolean) extends Msg
  }

  def update(msg: Msg, resizePaneFlow: Action[Int])(
      uiState: UiState
  ): (UiState, Action[Int], Cmd[AppMsg]) =
    msg match {
      case Msg.SetPaneFlowOpen(open) =>
        setPaneOpen(uiState, resizePaneFlow, PaneFlow.PaneName, open)

      case Msg.SetPaneStockOpen(open) =>
        setPaneOpen(uiState, resizePaneFlow, PaneStock.PaneName, open)
    }

  private def setPaneOpen(
      uiState: UiState,
      resizePaneFlow: Action[Int],
      name: String,
      open: Boolean
  ): (UiState, Action[Int], Cmd[AppMsg]) = {
    if (uiState.paneOpened(name) == open) {
      // the pane state won't change
      return (uiState, resizePaneFlow, Cmd.none)
    }

    uiState
      .setPaneOpen(name, open)
      .pipe { newState =>
        (
          newState.paneOpened(PaneFlow.PaneName),
          newState.paneOpened(PaneStock.PaneName)
        ) match {
          case (false, false) =>
            (uiState, resizePaneFlow) // Can't fold both panes at the same time
          case (true, true) =>
            if (uiState.paneOpened(PaneFlow.PaneName))
              // Retain the width of flow-pane when opening stock-pane
              (newState, resizePaneFlow.trigger(PaneFlow.currentWidth.toInt))
            else
              (newState, resizePaneFlow)
          case _ => (newState, resizePaneFlow)
        }
      }
      .pipe { case (state, resizePaneFlow) =>
        (
          state,
          resizePaneFlow,
          Cmd.Batch(
            state.save,
            resizeWindow(name, open, state).toNone
          )
        )
      }
  }

  def resizeWindow(
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

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

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
        resize = model.resizePaneFlow,
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
            dispatch(Msg.SetPaneFlowOpen(true))
          }
        )(
          Option.when(stockOpened)(flowPaneToggle),
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
            dispatch(Msg.SetPaneStockOpen(true))
          }
        )(
          Option.when(flowOpened)(stockPaneToggle),
          PaneStock(model, uiState)
        )
      )
    )
  }

  private def flowPaneToggle(implicit dispatch: AppMsg => Unit): ReactElement =
    paneToggle(
      onFoldClick = () => dispatch(Msg.SetPaneFlowOpen(false).into),
      onUnfoldClick = () => dispatch(Msg.SetPaneFlowOpen(true).into)
    )

  private def stockPaneToggle(implicit dispatch: AppMsg => Unit): ReactElement =
    paneToggle(
      onFoldClick = () => dispatch(Msg.SetPaneStockOpen(false).into),
      onUnfoldClick = () => dispatch(Msg.SetPaneStockOpen(true).into)
    )
}
