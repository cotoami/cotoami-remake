package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{FlowInputMsg, Model, Msg}
import cotoami.components.{materialSymbol, paneToggle, ToolButton}
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
          section(className := "post")(
            coto.repostOfId.map(_ => repostHeader(model, coto, dispatch)),
            cotoArticle(model, model.cotos.getOriginal(coto), dispatch)
          )
        ): _*
      )
    )

  def cotoArticle(
      model: Model,
      coto: Coto,
      dispatch: Msg => Unit
  ): ReactElement =
    article(className := "coto")(
      header()(
        ViewCoto.otherCotonomas(model, coto, dispatch),
        Option.when(Some(coto.postedById) != model.nodes.operatingId) {
          ViewCoto.author(model, coto)
        }
      ),
      div(className := "body")(
        ViewCoto.content(model, coto, dispatch)
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

  def repostHeader(
      model: Model,
      coto: Coto,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "repost-header")(
      materialSymbol("repeat"),
      Option.when(model.cotonomas.selectedId.isEmpty) {
        repostedIn(model, coto, dispatch)
      },
      Option.when(Some(coto.postedById) != model.nodes.operatingId) {
        reposter(model, coto)
      }
    )

  def repostedIn(
      model: Model,
      coto: Coto,
      dispatch: Msg => Unit
  ): Option[ReactElement] =
    coto.postedInId.flatMap(model.cotonomas.get).map(cotonoma =>
      a(
        className := "reposted-in",
        onClick := ((e) => {
          e.preventDefault()
          dispatch(cotoami.SelectCotonoma(cotonoma.id))
        })
      )(cotonoma.name)
    )

  def reposter(
      model: Model,
      coto: Coto
  ): ReactElement =
    address(className := "reposter")(
      model.nodes.get(coto.postedById).map(node =>
        Fragment(
          nodeImg(node),
          node.name
        )
      )
    )
}
