package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{FlowInputMsg, Model, Msg}
import cotoami.models.UiState

object PaneFlow {
  val PaneName = "PaneFlow"
  val DefaultWidth = 500

  val EditorPaneName = "PaneFlow.editor"
  val EditorDefaultHeight = 150

  def apply(
      model: Model,
      uiState: UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "flow")(
      (model.domain.nodes.operating, model.domain.currentCotonoma) match {
        case (Some(node), Some(cotonoma)) =>
          Some(
            FormCoto(
              model.flowInput,
              node,
              cotonoma,
              uiState.paneSizes.getOrElse(
                EditorPaneName,
                EditorDefaultHeight
              ),
              (newSize) =>
                dispatch(cotoami.ResizePane(EditorPaneName, newSize)),
              subMsg => dispatch(FlowInputMsg(subMsg))
            )
          )
        case _ => None
      },
      SectionTimeline(model, dispatch)
    )
}
