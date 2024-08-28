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
      query: String = "",
      scrollPos: Option[(Id[Cotonoma], Double)] = None,
      loading: Boolean = false,
      imeActive: Boolean = false
  ) {
    def init: Model =
      this.copy(
        cotoIds = PaginatedIds(),
        query = "",
        loading = true,
        imeActive = false
      )

    def saveScrollPos(key: Id[Cotonoma], pos: Double): Model =
      this.copy(scrollPos = Some((key, pos)))

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
    case object ClearQuery extends Msg
    case class QueryInput(query: String) extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case class ScrollAreaUnmounted(cotonomaId: Id[Cotonoma], scrollPos: Double)
        extends Msg
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
              _3 = Seq(fetch(Some(model.query), i))
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

      case Msg.ClearQuery =>
        default.copy(
          _1 = model.copy(query = ""),
          _3 = Seq(fetch(None, 0))
        )

      case Msg.QueryInput(query) =>
        default.copy(
          _1 = model.copy(query = query),
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
          _3 = Seq(fetch(Some(model.query), 0))
        )

      case Msg.ScrollAreaUnmounted(cotonomaId, scrollPos) =>
        default.copy(_1 = model.saveScrollPos(cotonomaId, scrollPos))
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
    context.domain.currentCotonomaId.flatMap(cotonomaId =>
      Option.when(
        !model.query.isBlank || !timeline.isEmpty || !waitingPosts.isEmpty
      )(
        sectionTimeline(model, timeline, waitingPosts, cotonomaId)
      )
    )
  }

  private def sectionTimeline(
      model: Model,
      cotos: Seq[Coto],
      waitingPosts: WaitingPosts,
      currentCotonomaId: Id[Cotonoma]
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    section(className := "timeline header-and-body")(
      header(className := "tools")(
        toolButton(
          symbol = "filter_list",
          tip = "Filter",
          classes = "filter"
        ),
        div(className := "search")(
          input(
            `type` := "search",
            name := "query",
            value := model.query,
            onChange := ((e) => dispatch(Msg.QueryInput(e.target.value).toApp)),
            onCompositionStart := (_ =>
              dispatch(Msg.ImeCompositionStart.toApp)
            ),
            onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd.toApp))
          ),
          Option.when(!model.query.isBlank) {
            button(
              className := "clear default",
              onClick := (_ => dispatch(Msg.ClearQuery.toApp))
            )(materialSymbol("close"))
          }
        )
      ),
      div(className := "posts body")(
        ScrollArea(
          initialScrollTop = model.getScrollPos(currentCotonomaId),
          onScrollToBottom = Some(() => dispatch(Msg.FetchMore.toApp)),
          onUnmounted = Some(scrollTop =>
            dispatch(
              Msg.ScrollAreaUnmounted(currentCotonomaId, scrollTop).toApp
            )
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
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val originalCoto = context.domain.cotos.getOriginal(coto)

    section(
      className := optionalClasses(
        Seq(
          ("post", true),
          ("posted", coto.posted)
        )
      ),
      key := coto.id.uuid
    )(
      repostHeader(coto),
      ViewCoto.ulParents(
        context.domain.parentsOf(originalCoto.id),
        AppMsg.FocusCoto(_)
      ),
      articleCoto(originalCoto),
      ViewCoto.divLinksTraversal(originalCoto, "bottom")
    )
  }

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
      ViewCoto.articleFooter(coto)
    )
  }

  private def repostHeader(coto: Coto)(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): Option[ReactElement] =
    Option.when(coto.repostOfId.isDefined) {
      val domain = context.domain

      section(className := "repost-header")(
        materialSymbol("repeat"),
        // Display the cotonomas to which the coto has been reposted
        // when the current location is a node home (no cotonoma is focused).
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
