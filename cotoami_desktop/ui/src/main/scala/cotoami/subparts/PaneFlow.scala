package cotoami.subparts

import org.scalajs.dom
import org.scalajs.dom.HTMLElement
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.components.toolButton
import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Coto, UiState}

object PaneFlow {

  final val PaneId = "flow-pane"
  final val PaneName = "PaneFlow"
  final val DefaultWidth = 600

  final val EditorPaneName = "PaneFlow.editor"
  final val EditorDefaultHeight = 300

  def widthIn(uiState: UiState): Int =
    uiState.paneSizes.getOrElse(PaneName, DefaultWidth)

  def currentWidth: Double = dom.document.getElementById(PaneId) match {
    case element: HTMLElement => element.offsetWidth
    case _                    => 0
  }

  def apply(
      model: Model,
      uiState: UiState
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(id := PaneId, className := "flow fill")(
      model.repo.cotos.focused.map(focusedCoto(_))
        .getOrElse(timeline(model, uiState))
    )

  private def timeline(
      model: Model,
      uiState: UiState
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Fragment(
      (model.repo.currentCotonoma, model.repo.canPostCoto) match {
        case (Some(cotonoma), true) =>
          Some(
            SectionFlowInput(
              model.flowInput,
              cotonoma,
              model.geomap,
              uiState.paneSizes.getOrElse(
                EditorPaneName,
                EditorDefaultHeight
              ),
              (newSize) => dispatch(AppMsg.ResizePane(EditorPaneName, newSize))
            )
          )
        case _ => None
      },
      SectionTimeline(model.timeline)
    )

  private def focusedCoto(
      coto: Coto
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "focused-coto header-and-body")(
      header(
        toolButton(
          classes = "back",
          symbol = "arrow_back",
          tip = Some(context.i18n.text.Back),
          tipPlacement = "right",
          onClick = _ => dom.window.history.back()
        ),
        toolButton(
          classes = "close",
          symbol = "close",
          onClick = _ => dispatch(AppMsg.UnfocusCoto)
        )
      ),
      div(className := "body")(
        SectionCotoDetails(coto)
      )
    )
}
