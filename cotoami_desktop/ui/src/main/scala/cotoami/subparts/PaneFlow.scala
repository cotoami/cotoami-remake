package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{FlowInputMsg, Model, Msg}
import cotoami.components.{materialSymbol, ScrollArea, ToolButton}
import cotoami.backend.Coto
import cotoami.repositories._

object PaneFlow {
  val PaneName = "PaneFlow"
  val DefaultWidth = 500

  val EditorPaneName = "PaneFlow.editor"
  val EditorDefaultHeight = 150

  def apply(
      model: Model,
      uiState: Model.UiState,
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
      Option.when(!model.domain.timeline.isEmpty)(
        sectionTimeline(model.domain.timeline, model, dispatch)
      )
    )

  private def sectionTimeline(
      cotos: Seq[Coto],
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "timeline header-and-body")(
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
        ScrollArea(
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => dispatch(Msg.FetchMoreTimeline)
        )(
          cotos.map(coto =>
            section(className := "post")(
              coto.repostOfId.map(_ =>
                repostHeader(coto, model.domain, dispatch)
              ),
              articleCoto(model.domain.cotos.getOriginal(coto), model, dispatch)
            )
          ) :+ div(
            className := "more",
            aria - "busy" := model.domain.cotos.timelineLoading.toString()
          )(): _*
        )
      )
    )

  private def articleCoto(
      coto: Coto,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    article(className := "coto")(
      header()(
        ViewCoto.otherCotonomas(coto, model.domain, dispatch),
        Option.when(Some(coto.postedById) != model.domain.nodes.operatingId) {
          ViewCoto.author(coto, model.domain.nodes)
        }
      ),
      div(className := "body")(
        ViewCoto.content(coto, s"timeline-${coto.id}", model, dispatch)
      ),
      footer()(
        time(
          className := "posted-at",
          title := model.context.formatDateTime(coto.createdAt)
        )(
          model.context.display(coto.createdAt)
        )
      )
    )

  private def repostHeader(
      coto: Coto,
      domain: Domain,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "repost-header")(
      materialSymbol("repeat"),
      Option.when(domain.cotonomas.selectedId.isEmpty) {
        repostedIn(coto, domain.cotonomas, dispatch)
      },
      Option.when(Some(coto.postedById) != domain.nodes.operatingId) {
        reposter(coto, domain.nodes)
      }
    )

  private def repostedIn(
      coto: Coto,
      cotonomas: Cotonomas,
      dispatch: Msg => Unit
  ): Option[ReactElement] =
    coto.postedInId.flatMap(cotonomas.get).map(cotonoma =>
      a(
        className := "reposted-in",
        onClick := ((e) => {
          e.preventDefault()
          dispatch(cotoami.SelectCotonoma(cotonoma.id))
        })
      )(cotonoma.name)
    )

  private def reposter(
      coto: Coto,
      nodes: Nodes
  ): ReactElement =
    address(className := "reposter")(
      nodes.get(coto.postedById).map(node =>
        Fragment(
          nodeImg(node),
          node.name
        )
      )
    )
}
