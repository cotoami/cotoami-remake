package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg => AppMsg}
import cotoami.models.UiState

object PaneFlow {
  final val PaneName = "PaneFlow"
  final val DefaultWidth = 500

  final val EditorPaneName = "PaneFlow.editor"
  final val EditorDefaultHeight = 150

  def apply(
      model: Model,
      uiState: UiState
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    section(className := "flow")(
      (model.domain.nodes.operating, model.domain.currentCotonoma) match {
        case (Some(operatingNode), Some(cotonoma)) =>
          model.domain.nodes.get(cotonoma.nodeId).flatMap(targetNode =>
            if (model.domain.nodes.postableTo(targetNode.id))
              Some(
                FormCoto(
                  model.flowInput,
                  operatingNode,
                  cotonoma,
                  uiState.paneSizes.getOrElse(
                    EditorPaneName,
                    EditorDefaultHeight
                  ),
                  (newSize) =>
                    dispatch(AppMsg.ResizePane(EditorPaneName, newSize))
                )(subMsg => dispatch(AppMsg.FlowInputMsg(subMsg)))
              )
            else
              None
          )

        case _ => None
      },
      model.domain.cotos.focused.map(SectionCotoDetails(_)(model, dispatch))
        .getOrElse(
          SectionTimeline(model.timeline, model.waitingPosts)(
            model,
            dispatch
          )
        )
    )
}
