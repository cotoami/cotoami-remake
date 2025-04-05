package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.libs.tauri

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.components.{optionalClasses, paneToggle, SplitPane}

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

  def update(msg: Msg)(uiState: UiState): (UiState, Cmd[AppMsg]) =
    msg match {
      case Msg.SetPaneFlowOpen(open) =>
        setPaneOpen(uiState, PaneFlow.PaneName, open)

      case Msg.SetPaneStockOpen(open) =>
        setPaneOpen(uiState, PaneStock.PaneName, open)
    }

  private def setPaneOpen(
      uiState: UiState,
      name: String,
      open: Boolean
  ): (UiState, Cmd[AppMsg]) = {
    if (uiState.paneOpened(name) == open) {
      // the pane state won't change
      return (uiState, Cmd.none)
    }

    uiState
      .pipe { state =>
        if (state.paneOpened(PaneFlow.PaneName))
          state.resizePane(PaneFlow.PaneName, PaneFlow.currentWidth.toInt)
        else
          state
      }
      .setPaneOpen(name, open)
      .pipe { newState =>
        (
          newState.paneOpened(PaneFlow.PaneName),
          newState.paneOpened(PaneStock.PaneName)
        ) match {
          case (false, false) =>
            uiState // Can't fold both panes at the same time
          case _ => newState
        }
      }
      .pipe { state =>
        (
          state,
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
