package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{FlowInputMsg, Model, Msg}
import cotoami.components.{paneToggle, ToolButton}
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
              (newSize) =>
                dispatch(cotoami.ResizePane(EditorPaneName, newSize)),
              subMsg => dispatch(FlowInputMsg(subMsg))
            )
          )
        case _ => None
      },
      section(className := "timeline header-and-body")(
        Option.when(!model.timeline.isEmpty)(
          timelineContent(model, model.timeline, dispatch)
        )
      )
    )

  def timelineContent(
      model: Model,
      cotos: Seq[Coto],
      dispatch: Msg => Unit
  ): ReactElement =
    Fragment(
      header(className := "tools")(
        ToolButton(
          classes = "filter",
          tip = "Filter",
          symbol = "filter_list"
        ),
        ToolButton(
          classes = "calendar",
          tip = "Calendar",
          symbol = "calendar_month"
        )
      ),
      div(className := "posts body")(
        cotos.map(coto =>
          article(className := "post coto")(
            header()(
              ViewCoto.otherCotonomas(model, coto, dispatch)
            ),
            div(className := "body")(
              ViewCoto.content(model, coto, dispatch)
            ),
            footer()(
              time(
                className := "posted_at",
                title := model.context.formatDateTime(coto.createdAt)
              )(
                model.context.display(coto.createdAt)
              )
            )
          )
        ): _*
      )
    )
}
