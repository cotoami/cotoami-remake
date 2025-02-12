package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState

object PaneFlow {
  final val PaneName = "PaneFlow"
  final val DefaultWidth = 500

  final val EditorPaneName = "PaneFlow.editor"
  final val EditorDefaultHeight = 300

  def apply(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "flow fill")(
      model.domain.cotos.focused.map(SectionCotoDetails(_)(model, dispatch))
        .getOrElse(timeline(model, uiState))
    )

  private def timeline(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Fragment(
      (
        model.domain.nodes.operating,
        model.domain.currentCotonoma,
        model.domain.canPost
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
            )(context, dispatch)
          )
        case _ => None
      },
      SectionTimeline(model.timeline, model.waitingPosts)(model, dispatch)
    )
}
