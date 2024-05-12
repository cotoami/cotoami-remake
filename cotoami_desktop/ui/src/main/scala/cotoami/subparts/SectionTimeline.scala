package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import com.softwaremill.quicklens._

import fui.FunctionalUI._
import cotoami.{Context, Model, SectionTimelineMsg}
import cotoami.backend.Coto
import cotoami.repositories._
import cotoami.components.{materialSymbol, ScrollArea, ToolButton}

object SectionTimeline {

  sealed trait Msg
  case object InitSearch extends Msg
  case object CloseSearch extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case InitSearch =>
        (model.modify(_.domain.cotos.query).setTo(Some("")), Seq.empty)

      case CloseSearch =>
        (
          model.modify(_.domain.cotos.query).setTo(None),
          Seq(
            Cotos.fetchTimeline(
              model.domain.nodes.selectedId,
              model.domain.cotonomas.selectedId,
              None,
              0
            )
          )
        )
    }

  def apply(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): Option[ReactElement] =
    Option.when(!model.domain.timeline.isEmpty)(
      sectionTimeline(model.domain.timeline, model, dispatch)
    )

  private def sectionTimeline(
      cotos: Seq[Coto],
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
          symbol = "calendar_month"
        ),
        model.domain.cotos.query.map(query =>
          form(className := "search")(
            input(`type` := "search", name := "query", value := query),
            button(
              className := "close default",
              onClick := (_ => dispatch(SectionTimelineMsg(CloseSearch)))
            )(
              materialSymbol("close")
            )
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
          onScrollToBottom = () => dispatch(cotoami.Msg.FetchMoreTimeline)
        )(
          cotos.map(
            sectionPost(_, model.domain, model.context, dispatch)
          ) :+ div(
            className := "more",
            aria - "busy" := model.domain.cotos.timelineLoading.toString()
          )(): _*
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
