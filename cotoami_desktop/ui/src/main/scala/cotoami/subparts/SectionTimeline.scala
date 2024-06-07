package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import com.softwaremill.quicklens._

import fui._
import cotoami.{Model, SectionTimelineMsg}
import cotoami.backend.Coto
import cotoami.repositories._
import cotoami.models.{Context, WaitingPost, WaitingPosts}
import cotoami.components.{materialSymbol, ScrollArea, ToolButton}

object SectionTimeline {

  sealed trait Msg
  case object InitSearch extends Msg
  case object CloseSearch extends Msg
  case class QueryInput(query: String) extends Msg
  case object ImeCompositionStart extends Msg
  case object ImeCompositionEnd extends Msg
  case object OpenCalendar extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case InitSearch =>
        (model.modify(_.domain.cotos.query).setTo(Some("")), Seq.empty)

      case CloseSearch =>
        (
          model.modify(_.domain.cotos.query).setTo(None),
          Seq(fetchDefaultTimeline(model))
        )

      case QueryInput(query) =>
        (
          model.modify(_.domain.cotos.query).setTo(Some(query)),
          if (model.imeActive)
            Seq.empty
          else
            Seq(fetchTimeline(query, model))
        )

      case ImeCompositionStart =>
        (model.modify(_.imeActive).setTo(true), Seq.empty)

      case ImeCompositionEnd =>
        (
          model.modify(_.imeActive).setTo(false),
          model.domain.cotos.query.map(query =>
            Seq(fetchTimeline(query, model))
          ).getOrElse(Seq.empty)
        )

      case OpenCalendar =>
        (model, Seq.empty)
    }

  private def fetchDefaultTimeline(model: Model): Cmd[cotoami.Msg] =
    Domain.fetchTimeline(
      model.domain.nodes.selectedId,
      model.domain.cotonomas.selectedId,
      None,
      0
    )

  private def fetchTimeline(query: String, model: Model): Cmd[cotoami.Msg] =
    if (query.isBlank())
      fetchDefaultTimeline(model)
    else
      Domain.fetchTimeline(
        model.domain.nodes.selectedId,
        model.domain.cotonomas.selectedId,
        Some(query),
        0
      )

  def apply(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): Option[ReactElement] =
    Option.when(
      !model.domain.timeline.isEmpty ||
        !model.waitingPosts.isEmpty ||
        model.domain.cotos.query.isDefined
    )(
      sectionTimeline(
        model.domain.timeline,
        model.waitingPosts,
        model,
        dispatch
      )
    )

  private def sectionTimeline(
      cotos: Seq[Coto],
      waitingPosts: WaitingPosts,
      model: Model,
      dispatch: cotoami.Msg => Unit
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
          symbol = "calendar_month",
          onClick = (() => dispatch(SectionTimelineMsg(OpenCalendar)))
        ),
        model.domain.cotos.query.map(query =>
          div(className := "search")(
            input(
              `type` := "search",
              name := "query",
              value := query,
              onChange := ((e) =>
                dispatch(SectionTimelineMsg(QueryInput(e.target.value)))
              ),
              onCompositionStart := (_ =>
                dispatch(SectionTimelineMsg(ImeCompositionStart))
              ),
              onCompositionEnd := (_ =>
                dispatch(SectionTimelineMsg(ImeCompositionEnd))
              )
            ),
            button(
              className := "close default",
              onClick := (_ => dispatch(SectionTimelineMsg(CloseSearch)))
            )(materialSymbol("close"))
          )
        ).getOrElse(
          ToolButton(
            classes = "search",
            tip = "Search",
            symbol = "search",
            onClick = (() => dispatch(SectionTimelineMsg(InitSearch)))
          )
        )
      ),
      div(className := "posts body")(
        ScrollArea(
          scrollableElementId = None,
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => dispatch(Cotos.FetchMoreTimeline.asAppMsg)
        )(
          (waitingPosts.posts.map(sectionWaitingPost(_, model.domain)) ++
            cotos.map(
              sectionPost(_, model.domain, model.context, dispatch)
            ) :+ div(
              className := "more",
              aria - "busy" := model.domain.cotos.timelineLoading.toString()
            )()): _*
        )
      )
    )

  private def sectionWaitingPost(
      post: WaitingPost,
      domain: Domain
  ): ReactElement =
    section(className := "waiting-post", aria - "busy" := "true")(
      article(className := "coto")(
        post.error.map(section(className := "error")(_)),
        div(className := "body")(
          ViewCoto.divWaitingPostContent(post, domain)
        )
      )
    )

  private def sectionPost(
      coto: Coto,
      domain: Domain,
      context: Context,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "post")(
      coto.repostOfId.map(_ => repostHeader(coto, domain, dispatch)),
      ViewCoto.ulParents(domain.parentsOf(coto.id), dispatch),
      articleCoto(
        domain.cotos.getOriginal(coto),
        domain,
        context,
        dispatch
      ),
      ViewCoto.divLinksTraversal(coto, "top", dispatch)
    )

  private def articleCoto(
      coto: Coto,
      domain: Domain,
      context: Context,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    article(className := "coto")(
      header()(
        ViewCoto.divClassifiedAs(coto, domain, dispatch),
        Option.when(Some(coto.postedById) != domain.nodes.operatingId) {
          ViewCoto.addressAuthor(coto, domain.nodes)
        }
      ),
      div(className := "body")(
        ViewCoto.divContent(coto, domain, dispatch)
      ),
      footer()(
        time(
          className := "posted-at",
          title := context.formatDateTime(coto.createdAt)
        )(
          context.display(coto.createdAt)
        )
      )
    )

  private def repostHeader(
      coto: Coto,
      domain: Domain,
      dispatch: cotoami.Msg => Unit
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
      dispatch: cotoami.Msg => Unit
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
