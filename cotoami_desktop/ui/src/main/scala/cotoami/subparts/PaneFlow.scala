package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{FlowInputMsg, Model, Msg, ResizePane}
import cotoami.components.{paneToggle, Markdown, ToolButton}
import cotoami.backend.Coto

object PaneFlow {
  val EditorPaneName = "PaneFlow.editor"
  val EditorDefaultHeight = 150

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement = {
    val timelineCotos = model.cotos.timeline
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
        Option.when(!timelineCotos.isEmpty)(
          timelineContent(model, timelineCotos, dispatch)
        )
      )
    )
  }

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
            header()(),
            div(className := "body")(
              div(className := "content")(
                section(className := "text-content")(
                  Markdown()(coto.content)
                )
              )
            ),
            footer()(
              time(className := "posted_at")(
                model.context.toDateTime(coto.createdAt).toString()
              )
            )
          )
        ): _*
      )
    )
}
