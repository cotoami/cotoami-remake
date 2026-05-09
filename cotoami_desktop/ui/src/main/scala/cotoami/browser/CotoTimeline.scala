package cotoami.browser

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.subparts.{SectionFlowInput, SectionTimeline}

object CotoTimeline {

  final val EditorPaneName = "BrowserTimeline.editor"
  final val DefaultEditorHeight = 180

  case class Parts(
      timeline: Option[ReactElement],
      cotonomaSelect: Option[ReactElement]
  )

  def apply(
      model: App.Model,
      timelineEditorHeight: Int,
      onTimelineEditorHeightChange: Int => Unit,
      onCotonomaSelectMsg: CotonomaSelect.Msg => Unit
  )(using Context, Into[AppMsg] => Unit): Parts =
    Parts(
      timeline = model.databaseFolder.map(_ =>
        Fragment(
          (model.app.repo.currentCotonoma, model.app.repo.canPostCoto) match {
            case (Some(cotonoma), true) =>
              Some(
                SectionFlowInput(
                  model.app.flowInput,
                  cotonoma,
                  model.app.geomap,
                  timelineEditorHeight,
                  onTimelineEditorHeightChange,
                  SectionFlowInput.Options(
                    showOpenNewCotoModal = false,
                    allowCotonomaForm = false,
                    showPostTo = false,
                    persistDraft = false
                  )
                )
              )
            case _ => None
          },
          SectionTimeline(
            model.app.timeline,
            SectionTimeline.Options(showItoTraversalParts = false)
          ).getOrElse(div(className := "browser-timeline-empty")())
        )
      ),
      cotonomaSelect = model.databaseFolder.map(_ =>
        CotonomaSelect.view(
          model.cotonomaSelect,
          model.app,
          onCotonomaSelectMsg
        )
      )
    )
}
