package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import fui._
import cotoami.{log_info, Context, Msg => AppMsg}
import cotoami.backend.{
  Coto,
  Cotonoma,
  ErrorJson,
  Id,
  Node,
  PaginatedCotos,
  PaginatedIds
}
import cotoami.repositories._
import cotoami.models.{WaitingPost, WaitingPosts}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  ScrollArea,
  ToolButton
}

object SectionTimeline {

  case class Model(
      cotoIds: PaginatedIds[Coto] = PaginatedIds(),
      query: Option[String] = None,
      loading: Boolean = false,
      imeActive: Boolean = false
  ) {
    def appendPage(cotos: PaginatedCotos): Model =
      this
        .modify(_.cotoIds).using(_.appendPage(cotos.page))
        .modify(_.loading).setTo(false)
  }

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.SectionTimelineMsg(this)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen AppMsg.SectionTimelineMsg

    case object FetchMore extends Msg
    case class Fetched(result: Either[ErrorJson, PaginatedCotos]) extends Msg
    case object InitSearch extends Msg
    case object CloseSearch extends Msg
    case class QueryInput(query: String) extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case object OpenCalendar extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Domain, Seq[Cmd[AppMsg]]) = {
    val default = (model, context.domain, Seq.empty)
    msg match {
      case Msg.FetchMore =>
        if (model.loading)
          default
        else
          model.cotoIds.nextPageIndex.map(i =>
            default.copy(
              _1 = model.copy(loading = true),
              _3 = Seq(fetch(model.query, i))
            )
          ).getOrElse(default)

      case Msg.Fetched(Right(cotos)) =>
        default.copy(
          _1 = model.appendPage(cotos),
          _2 = context.domain.importFrom(cotos),
          _3 = Seq(
            log_info("Timeline fetched.", Some(cotos.debug))
          )
        )

      case Msg.Fetched(Left(e)) =>
        default.copy(
          _1 = model.copy(loading = false),
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch timeline cotos."))
        )

      case Msg.InitSearch =>
        default.copy(_1 = model.copy(query = Some("")))

      case Msg.CloseSearch =>
        default.copy(
          _1 = model.copy(query = None),
          _3 = Seq(fetch(None, 0))
        )

      case Msg.QueryInput(query) =>
        default.copy(
          _1 = model.copy(query = Some(query)),
          _3 =
            if (model.imeActive)
              Seq.empty
            else
              Seq(fetch(Some(query), 0))
        )

      case Msg.ImeCompositionStart =>
        default.copy(_1 = model.copy(imeActive = true))

      case Msg.ImeCompositionEnd =>
        default.copy(
          _1 = model.copy(imeActive = false),
          _3 = Seq(fetch(model.query, 0))
        )

      case Msg.OpenCalendar => default
    }
  }

  def fetch(
      query: Option[String],
      pageIndex: Double
  )(implicit context: Context): Cmd[AppMsg] =
    fetch(
      context.domain.nodes.selectedId,
      context.domain.cotonomas.selectedId,
      query,
      pageIndex
    )

  def fetch(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      query: Option[String],
      pageIndex: Double
  ): Cmd[AppMsg] =
    query.map(query =>
      if (query.isBlank())
        PaginatedCotos.fetchRecent(nodeId, cotonomaId, pageIndex)
      else
        PaginatedCotos.search(query, nodeId, cotonomaId, pageIndex)
    ).getOrElse(
      PaginatedCotos.fetchRecent(nodeId, cotonomaId, pageIndex)
    ).map(Msg.toApp(Msg.Fetched))

  def apply(
      model: Model,
      waitingPosts: WaitingPosts,
      dispatch: AppMsg => Unit
  )(implicit context: Context): Option[ReactElement] =
    Option.when(
      !context.domain.timeline.isEmpty ||
        !waitingPosts.isEmpty ||
        context.domain.cotos.query.isDefined
    )(
      sectionTimeline(
        context.domain.timeline,
        waitingPosts,
        dispatch
      )
    )

  private def sectionTimeline(
      cotos: Seq[Coto],
      waitingPosts: WaitingPosts,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
    val domain = context.domain
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
          onClick = (() => dispatch(Msg.OpenCalendar.toApp))
        ),
        domain.cotos.query.map(query =>
          div(className := "search")(
            input(
              `type` := "search",
              name := "query",
              value := query,
              onChange := ((e) =>
                dispatch(Msg.QueryInput(e.target.value).toApp)
              ),
              onCompositionStart := (_ =>
                dispatch(Msg.ImeCompositionStart.toApp)
              ),
              onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd.toApp))
            ),
            button(
              className := "close default",
              onClick := (_ => dispatch(Msg.CloseSearch.toApp))
            )(materialSymbol("close"))
          )
        ).getOrElse(
          ToolButton(
            classes = "search",
            tip = "Search",
            symbol = "search",
            onClick = (() => dispatch(Msg.InitSearch.toApp))
          )
        )
      ),
      div(className := "posts body")(
        ScrollArea(
          scrollableElementId = None,
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => dispatch(Msg.FetchMore.toApp)
        )(
          (waitingPosts.posts.map(sectionWaitingPost(_)) ++
            cotos.map(
              sectionPost(_, dispatch)
            ) :+ div(
              className := "more",
              aria - "busy" := domain.cotos.timelineLoading.toString()
            )()): _*
        )
      )
    )
  }

  private def sectionWaitingPost(
      post: WaitingPost
  )(implicit context: Context): ReactElement =
    section(
      className := "waiting-post",
      key := post.postId,
      aria - "busy" := "true"
    )(
      article(className := "coto")(
        post.error.map(section(className := "error")(_)),
        div(className := "body")(
          ViewCoto.divWaitingPostContent(post)
        )
      )
    )

  private def sectionPost(
      coto: Coto,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("post", true),
          ("posted", coto.posted)
        )
      ),
      key := coto.id.uuid
    )(
      coto.repostOfId.map(_ => repostHeader(coto, dispatch)),
      ViewCoto.ulParents(context.domain.parentsOf(coto.id), dispatch),
      articleCoto(
        context.domain.cotos.getOriginal(coto),
        dispatch
      ),
      ViewCoto.divLinksTraversal(coto, "top", dispatch)
    )

  private def articleCoto(coto: Coto, dispatch: AppMsg => Unit)(implicit
      context: Context
  ): ReactElement = {
    val domain = context.domain
    article(className := "coto")(
      header()(
        ViewCoto.divClassifiedAs(coto, dispatch),
        Option.when(Some(coto.postedById) != domain.nodes.operatingId) {
          ViewCoto.addressAuthor(coto, domain.nodes)
        }
      ),
      div(className := "body")(
        ViewCoto.divContent(coto, dispatch)
      ),
      footer()(
        time(
          className := "posted-at",
          title := context.time.formatDateTime(coto.createdAt)
        )(
          context.time.display(coto.createdAt)
        )
      )
    )
  }

  private def repostHeader(coto: Coto, dispatch: AppMsg => Unit)(implicit
      context: Context
  ): ReactElement = {
    val domain = context.domain
    section(className := "repost-header")(
      materialSymbol("repeat"),
      Option.when(domain.cotonomas.selectedId.isEmpty) {
        repostedIn(coto, domain.cotonomas, dispatch)
      },
      Option.when(Some(coto.postedById) != domain.nodes.operatingId) {
        reposter(coto, domain.nodes)
      }
    )
  }

  private def repostedIn(
      coto: Coto,
      cotonomas: Cotonomas,
      dispatch: AppMsg => Unit
  ): Option[ReactElement] =
    coto.postedInId.flatMap(cotonomas.get).map(cotonoma =>
      a(
        className := "reposted-in",
        onClick := ((e) => {
          e.preventDefault()
          dispatch(AppMsg.SelectCotonoma(cotonoma))
        })
      )(cotonoma.name)
    )

  private def reposter(coto: Coto, nodes: Nodes): ReactElement =
    address(className := "reposter")(
      nodes.get(coto.postedById).map(spanNode)
    )
}
