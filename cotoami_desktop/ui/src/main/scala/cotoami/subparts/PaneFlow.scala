package cotoami.subparts

import org.scalajs.dom
import org.scalajs.dom.HTMLElement
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState

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
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(id := PaneId, className := "flow fill")(
      model.repo.cotos.focused.map(SectionCotoDetails(_))
        .getOrElse(timeline(model, uiState))
    )

  private def timeline(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Fragment(
      (
        model.repo.nodes.operating,
        model.repo.currentCotonoma,
        model.repo.canPost
      ) match {
        case (Some(operatingNode), Some(cotonoma), true) =>
          Some(
            SectionFlowInput(
              model.flowInput,
              operatingNode,
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
}
