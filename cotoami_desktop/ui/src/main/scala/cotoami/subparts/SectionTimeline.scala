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
  toolButton,
  ScrollArea
}

object SectionTimeline {

  case class Model(
      cotoIds: PaginatedIds[Coto] = PaginatedIds(),
      query: Option[String] = None,
      scrollPos: Option[(Id[Cotonoma], Double)] = None,
      loading: Boolean = false,
      imeActive: Boolean = false
  ) {
    def init: Model =
      this.copy(
        cotoIds = PaginatedIds(),
        query = None,
        loading = true,
        imeActive = false
      )

    def saveScrollPos(key: Id[Cotonoma], pos: Double): Model =
      this.modify(_.scrollPos).setTo(Some((key, pos)))

    def getScrollPos(key: Id[Cotonoma]): Option[Double] =
      this.scrollPos.flatMap(pos => Option.when(pos._1 == key)(pos._2))

    def appendPage(cotos: PaginatedCotos): Model =
      this
        .modify(_.cotoIds).using(_.appendPage(cotos.page))
        .modify(_.loading).setTo(false)

    def timeline()(implicit context: Context): Seq[Coto] = {
      val rawTimeline = this.cotoIds.order.map(context.domain.cotos.get).flatten
      context.domain.nodes.current.map(node =>
        rawTimeline.filter(_.nameAsCotonoma != Some(node.name))
      ).getOrElse(rawTimeline)
    }

    def post(cotoId: Id[Coto]): Model =
      this
        .modify(_.cotoIds).using(cotoIds =>
          if (this.query.isEmpty)
            cotoIds.prependId(cotoId)
          else
            cotoIds
        )
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
    case class ScrollAreaUnmounted(scrollPos: Double) extends Msg
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

      case Msg.ScrollAreaUnmounted(scrollPos) =>
        context.domain.currentCotonomaId.map(cotonomaId =>
          default.copy(_1 = model.saveScrollPos(cotonomaId, scrollPos))
        ).getOrElse(default)
    }
  }

  def fetch(
      query: Option[String],
      pageIndex: Double
  )(implicit context: Context): Cmd[AppMsg] =
    fetch(
      context.domain.nodes.focusedId,
      context.domain.cotonomas.focusedId,
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
      waitingPosts: WaitingPosts
  )(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): Option[ReactElement] = {
    val timeline = model.timeline()
    Option.when(
      model.query.isDefined || !timeline.isEmpty || !waitingPosts.isEmpty
    )(
      sectionTimeline(model, timeline, waitingPosts)
    )
  }

  private def sectionTimeline(
      model: Model,
      cotos: Seq[Coto],
      waitingPosts: WaitingPosts
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    section(className := "timeline header-and-body")(
      header(className := "tools")(
        toolButton(
          symbol = "filter_list",
          tip = "Filter",
          classes = "filter"
        ),
        model.query.map(query =>
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
          toolButton(
            symbol = "search",
            tip = "Search",
            classes = "search",
            onClick = _ => dispatch(Msg.InitSearch.toApp)
          )
        )
      ),
      div(className := "posts body")(
        ScrollArea(
          initialScrollTop =
            context.domain.currentCotonomaId.flatMap(model.getScrollPos(_)),
          onScrollToBottom = Some(() => dispatch(Msg.FetchMore.toApp)),
          onUnmounted = Some(scrollTop =>
            dispatch(Msg.ScrollAreaUnmounted(scrollTop).toApp)
          )
        )(
          (waitingPosts.posts.map(sectionWaitingPost(_)) ++
            cotos.map(sectionPost) :+
            div(
              className := "more",
              aria - "busy" := model.loading.toString()
            )()): _*
        )
      )
    )

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
      coto: Coto
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("post", true),
          ("posted", coto.posted)
        )
      ),
      key := coto.id.uuid
    )(
      coto.repostOfId.map(_ => repostHeader(coto)),
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id),
        AppMsg.FocusCoto(_)
      ),
      articleCoto(
        context.domain.cotos.getOriginal(coto)
      ),
      ViewCoto.divLinksTraversal(coto, "bottom")
    )

  private def articleCoto(coto: Coto)(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val domain = context.domain
    article(
      className := "coto",
      onClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
    )(
      header()(
        ViewCoto.divClassifiedAs(coto),
        Option.when(Some(coto.postedById) != domain.nodes.operatingId) {
          ViewCoto.addressAuthor(coto, domain.nodes)
        }
      ),
      div(className := "body")(
        ViewCoto.divContent(coto)
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

  private def repostHeader(coto: Coto)(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val domain = context.domain
    section(className := "repost-header")(
      materialSymbol("repeat"),
      Option.when(domain.cotonomas.focusedId.isEmpty) {
        repostedIn(coto, domain.cotonomas)
      },
      Option.when(Some(coto.postedById) != domain.nodes.operatingId) {
        reposter(coto, domain.nodes)
      }
    )
  }

  private def repostedIn(
      coto: Coto,
      cotonomas: Cotonomas
  )(implicit dispatch: AppMsg => Unit): Option[ReactElement] =
    coto.postedInId.flatMap(cotonomas.get).map(cotonoma =>
      a(
        className := "reposted-in",
        onClick := ((e) => {
          e.preventDefault()
          dispatch(AppMsg.FocusCotonoma(cotonoma))
        })
      )(cotonoma.name)
    )

  private def reposter(coto: Coto, nodes: Nodes): ReactElement =
    address(className := "reposter")(
      nodes.get(coto.postedById).map(spanNode)
    )
}
