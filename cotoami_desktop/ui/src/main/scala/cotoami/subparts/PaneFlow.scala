package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{FlowInputMsg, Model, Msg, ResizePane}
import cotoami.components.paneToggle
import cotoami.backend.Coto

object PaneFlow {
  val EditorPaneName = "PaneFlow.editor"
  val EditorDefaultHeight = 150

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "flow pane")(
      paneToggle("flow", dispatch),
      (model.nodes.operating, model.currentCotonoma) match {
        case (Some(node), Some(cotonoma)) =>
          Some(
            FormCoto.view(
              model.flowInput,
              node,
              cotonoma,
              uiState.paneSizes.getOrElse(
                EditorPaneName,
                EditorDefaultHeight
              ),
              (newSize) => dispatch(ResizePane(EditorPaneName, newSize)),
              subMsg => dispatch(FlowInputMsg(subMsg))
            )
          )
        case _ => None
      },
      section(className := "timeline header-and-body")(
      )
    )

  def timelineContent(
      model: Model,
      cotos: Seq[Coto],
      dispatch: Msg => Unit
  ): Seq[ReactElement] = Seq(
    header(className := "tools")(
    )
  )
}
