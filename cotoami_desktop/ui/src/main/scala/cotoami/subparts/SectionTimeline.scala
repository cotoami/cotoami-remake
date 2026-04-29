package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import marubinotto.fui._
import marubinotto.components.{
  materialSymbol,
  materialSymbolFilled,
  toolButton,
  Flipped,
  Flipper,
  ScrollArea
}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{
  Coto,
  Cotonoma,
  Id,
  Node,
  PaginatedIds,
  Scope,
  WaitingPost,
  WaitingPosts
}
import cotoami.repository._
import cotoami.backend.{ErrorJson, NodeBackend, PaginatedCotos}

object SectionTimeline {
  val LatestScrollTopThreshold = 48

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      cotoIds: PaginatedIds[Coto] = PaginatedIds(),

      // Coto/Cotonoma inputs waiting to be posted
      waitingPosts: WaitingPosts = WaitingPosts(),

      // Filter criteria for cotos in this timeline
      onlyCotonomas: Boolean = false,
      queryInput: String = "",
      queryInputKey: Int = 0,

      // To avoid rendering old results unintentionally
      fetchNumber: Int = 0,

      // To restore the scroll position when back to timeline
      scrollPos: Option[(Id[Cotonoma], Double)] = None,

      // State
      atLatest: Boolean = true,
      newPostsAvailable: Boolean = false,
      scrollToLatestKey: Int = 0,
      imeActive: Boolean = false,
      loading: Boolean = false,
      markingAsRead: Boolean = false
  ) {
    def onFocusChange(using context: Context): (Model, Cmd.One[AppMsg]) =
      copy(
        cotoIds = PaginatedIds(),
        waitingPosts = WaitingPosts(),
        onlyCotonomas = false,
        queryInput = "",
        queryInputKey = queryInputKey + 1,
        fetchNumber = 0,
        atLatest = true,
        newPostsAvailable = false,
        loading = false,
        imeActive = false
      ).fetchFirst

    def cotos(using context: Context): Seq[Coto] = {
      val repo = context.repo
      val rootCotoId = repo.currentNodeRootCotonoma.map(_.cotoId)
      cotoIds.order.filter(Some(_) != rootCotoId).map(repo.cotos.get).flatten
    }

    def appendPage(cotos: PaginatedCotos, fetchNumber: Int): Model =
      this
        .modify(_.cotoIds).using(_.appendPage(cotos.page))
        .modify(_.fetchNumber).setTo(fetchNumber)
        .modify(_.loading).setTo(false)

    def saveScrollPos(key: Id[Cotonoma], pos: Double): Model =
      copy(scrollPos = Some((key, pos)))

    def getScrollPos(key: Id[Cotonoma]): Option[Double] =
      scrollPos.flatMap(pos => Option.when(pos._1 == key)(pos._2))

    def post(cotoId: Id[Coto]): Model =
      if (queryInput.isEmpty)
        copy(
          cotoIds = cotoIds.prependId(cotoId),
          newPostsAvailable = !atLatest,
          scrollToLatestKey =
            if (atLatest) scrollToLatestKey + 1 else scrollToLatestKey
        )
      else
        this

    def onScroll(scrollTop: Double): Model = {
      val atLatest = scrollTop <= LatestScrollTopThreshold
      copy(
        atLatest = atLatest,
        newPostsAvailable = if (atLatest) false else newPostsAvailable
      )
    }

    def jumpToLatest: Model =
      copy(
        atLatest = true,
        newPostsAvailable = false,
        scrollToLatestKey = scrollToLatestKey + 1
      )

    def fetchFirst(using context: Context): (Model, Cmd.One[AppMsg]) =
      fetching(resetLatestState = true).pipe { model =>
        (
          model,
          fetchInFocus(
            context.repo,
            onlyCotonomas,
            Some(queryInput),
            0,
            model.fetchNumber
          )
        )
      }

    def fetchMore(using context: Context): (Model, Cmd.One[AppMsg]) =
      if (loading)
        (this, Cmd.none)
      else
        cotoIds.nextPageIndex.map(i =>
          fetching().pipe { nextModel =>
            (
              nextModel,
              fetchInFocus(
                context.repo,
                onlyCotonomas,
                Some(queryInput),
                i,
                nextModel.fetchNumber
              )
            )
          }
        ).getOrElse((this, Cmd.none)) // no more

    private def fetching(resetLatestState: Boolean = false): Model =
      copy(
        fetchNumber = fetchNumber + 1,
        atLatest = if (resetLatestState) true else atLatest,
        newPostsAvailable =
          if (resetLatestState) false else newPostsAvailable,
        loading = true
      )

    def inputQuery(
        query: String
    )(using context: Context): (Model, Cmd[AppMsg]) =
      if (imeActive)
        (copy(queryInput = query), Cmd.none)
      else
        copy(queryInput = query).fetchFirst

    def readyToMarkAsRead(using context: Context): Boolean =
      context.repo.nodes.anyUnreadPostsInFocus && !markingAsRead
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.SectionTimelineMsg(this)
  }

  object Msg {
    case class SetOnlyCotonomas(onlyCotonomas: Boolean) extends Msg
    case class QueryInput(query: String) extends Msg
    case object ClearQuery extends Msg
    case object ImeCompositionStart extends Msg
    case class ImeCompositionEnd(query: String) extends Msg
    case object FetchMore extends Msg
    case class Fetched(number: Int, result: Either[ErrorJson, PaginatedCotos])
        extends Msg
    case class ScrollAreaUnmounted(cotonomaId: Id[Cotonoma], scrollPos: Double)
        extends Msg
    case class ScrollTopChanged(scrollTop: Double) extends Msg
    case object JumpToLatest extends Msg
    case object MarkAsRead extends Msg
    case class MarkedAsRead(
        nodeId: Option[Id[Node]],
        result: Either[ErrorJson, String]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(using
      context: Context
  ): (Model, Root, Cmd[AppMsg]) = {
    val default = (model, context.repo, Cmd.none)
    msg match {
      case Msg.SetOnlyCotonomas(onlyCotonomas) =>
        model
          .modify(_.cotoIds).setTo(PaginatedIds())
          .modify(_.onlyCotonomas).setTo(onlyCotonomas)
          .fetchFirst
          .pipe { case (model, cmd) =>
            default.copy(_1 = model, _3 = cmd)
          }

      case Msg.QueryInput(query) =>
        model.inputQuery(query).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.ClearQuery =>
        model
          .inputQuery("")
          .pipe { case (nextModel, cmd) =>
            default.copy(_1 = nextModel, _3 = cmd)
          }
          .pipe { case (next, repo, cmd) =>
            (next.modify(_.queryInputKey).using(_ + 1), repo, cmd)
          }

      case Msg.ImeCompositionStart =>
        default.copy(_1 = model.copy(imeActive = true))

      case Msg.ImeCompositionEnd(query) =>
        model
          .copy(imeActive = false)
          .inputQuery(query)
          .pipe { case (model, cmd) =>
            default.copy(_1 = model, _3 = cmd)
          }

      case Msg.FetchMore =>
        model.fetchMore.pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Fetched(number, Right(cotos)) =>
        if (number == model.fetchNumber)
          default.copy(
            _1 = model.appendPage(cotos, number),
            _2 = context.repo.importFrom(cotos)
          )
        else
          default

      case Msg.Fetched(_, Left(e)) =>
        default.copy(
          _1 = model.copy(loading = false),
          _3 = cotoami.error("Couldn't fetch timeline cotos.", e)
        )

      case Msg.ScrollAreaUnmounted(cotonomaId, scrollPos) =>
        default.copy(_1 = model.saveScrollPos(cotonomaId, scrollPos))

      case Msg.ScrollTopChanged(scrollTop) =>
        default.copy(_1 = model.onScroll(scrollTop))

      case Msg.JumpToLatest =>
        default.copy(_1 = model.jumpToLatest)

      case Msg.MarkAsRead => {
        val focusedNodeId = context.repo.nodes.focusedId
        default.copy(
          _1 = model.copy(markingAsRead = true),
          _3 = NodeBackend.markAsRead(focusedNodeId)
            .map(Msg.MarkedAsRead(focusedNodeId, _).into)
        )
      }

      case Msg.MarkedAsRead(nodeId, Right(utcIso)) => {
        val repo = context.repo.modify(_.nodes).using { nodes =>
          nodeId.map(nodes.markAsRead(_, utcIso))
            .getOrElse(nodes.markAllAsRead(utcIso))
        }
        default.copy(
          _1 = model.copy(markingAsRead = false),
          _2 = repo,
          _3 = repo.updateUnreadBadge
        )
      }

      case Msg.MarkedAsRead(nodeId, Left(e)) =>
        default.copy(
          _1 = model.copy(markingAsRead = false),
          _3 = cotoami.error("Couldn't mark as read.", e)
        )
    }
  }

  private def fetchInFocus(
      repo: Root,
      onlyCotonomas: Boolean,
      query: Option[String],
      pageIndex: Double,
      fetchNumber: Int
  ): Cmd.One[AppMsg] =
    fetch(
      repo.nodes.focusedId,
      repo.cotonomas.focusedId,
      onlyCotonomas,
      query,
      pageIndex,
      fetchNumber
    )

  private def fetch(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      onlyCotonomas: Boolean,
      query: Option[String],
      pageIndex: Double,
      fetchNumber: Int
  ): Cmd.One[AppMsg] =
    (query match {
      case Some(query) if !query.isBlank() =>
        PaginatedCotos.search(
          query,
          toScope(nodeId, cotonomaId),
          onlyCotonomas,
          pageIndex
        )
      case _ =>
        PaginatedCotos.fetchRecent(
          toScope(nodeId, cotonomaId),
          onlyCotonomas,
          pageIndex
        )
    }).map(Msg.Fetched(fetchNumber, _).into)

  private def toScope(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]]
  ): Scope =
    cotonomaId
      .map(Scope.ByCotonoma(_))
      .orElse(nodeId.map(Scope.ByNode(_)))
      .getOrElse(Scope.All)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  case class Options(
      showItoTraversalParts: Boolean = true
  )

  def apply(
      model: Model,
      options: Options = Options()
  )(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    val cotos = model.cotos
    context.repo.currentCotonomaId.flatMap(cotonomaId =>
      Option.when(
        !model.queryInput.isBlank || !cotos.isEmpty || !model.waitingPosts.isEmpty
      )(
        sectionTimeline(model, cotos, cotonomaId, options)
      )
    )
  }

  private def sectionTimeline(
      model: Model,
      cotos: Seq[Coto],
      currentCotonomaId: Id[Cotonoma],
      options: Options
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "timeline header-and-body")(
      headerTools(model),
      div(className := "coto-flow body")(
        ScrollArea(
          setScrollTop = model.getScrollPos(currentCotonomaId),
          scrollToTopWhen = Option.when(model.scrollToLatestKey > 0)(
            model.scrollToLatestKey.toString()
          ),
          onScrollToBottom = Some(() => dispatch(Msg.FetchMore)),
          onScrollTopChange =
            Some(scrollTop => dispatch(Msg.ScrollTopChanged(scrollTop))),
          onUnmounted = Some(scrollTop =>
            dispatch(
              Msg.ScrollAreaUnmounted(currentCotonomaId, scrollTop)
            )
          )
        )(
          Flipper(
            element = "div",
            className = "posts",
            flipKey = cotos.length.toString()
          )(
            (model.waitingPosts.posts.map(sectionWaitingPost) ++
              cotos.map { coto =>
                Flipped(key = coto.id.uuid, flipId = coto.id.uuid)(
                  sectionPost(coto, options)
                ): ReactElement
              } :+
              div(
                className := "more",
                aria - "busy" := model.loading.toString()
              )())*
          )
        ),
        Option.when(model.newPostsAvailable) {
          button(
            className := "new-message-indicator default",
            `type` := "button",
            aria - "label" := "Jump to latest message",
            onClick := (_ => dispatch(Msg.JumpToLatest))
          )(
            materialSymbol("arrow_upward")
          )
        }
      )
    )

  private def headerTools(
      model: Model
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    header(className := "tools")(
      Option.when(
        context.repo.cotonomas.focusedId.isEmpty &&
          context.repo.nodes.anyOthersPostsInFocus
      )(
        button(
          className := "mark-as-read contrast outline",
          disabled := !model.readyToMarkAsRead,
          aria - "busy" := model.markingAsRead.toString(),
          onClick := (_ => dispatch(Msg.MarkAsRead))
        )(context.i18n.text.MarkAllAsRead)
      ),
      div(className := "search")(
        input(
          `type` := "search",
          name := "query",
          key := model.queryInputKey.toString(),
          defaultValue := model.queryInput,
          onChange := ((e) => dispatch(Msg.QueryInput(e.target.value))),
          onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
          onCompositionEnd := (e =>
            dispatch(Msg.ImeCompositionEnd(e.target.value))
          )
        ),
        Option.when(!model.queryInput.isBlank) {
          button(
            className := "clear default",
            onClick := (_ => dispatch(Msg.ClearQuery))
          )(materialSymbol("close"))
        }
      )
    )

  private def sectionWaitingPost(
      post: WaitingPost
  )(using context: Context): ReactElement =
    section(
      className := "waiting-post flow-entry",
      key := post.postId,
      aria - "busy" := "true"
    )(
      article(className := "coto")(
        post.error.map(section(className := "error")(_)),
        div(className := "body")(
          PartsCoto.divWaitingPostContent(post)
        )
      )
    )

  private def sectionPost(
      post: Coto,
      options: Options
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    section(className := "post flow-entry")(
      context.repo.cotos.getOriginal(post)
        .map(articleCoto(_, post, options))
    )
  }

  private def articleCoto(coto: Coto, post: Coto, options: Options)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val repo = context.repo
    PartsCoto.article(coto, dispatch)(
      Option.when(repo.nodes.unread(post))(
        materialSymbolFilled("brightness_1", "unread-mark")
      ),
      ToolbarCoto(coto),
      sectionRepostHeader(post),
      Option.when(options.showItoTraversalParts) {
        PartsCoto.ulParents(
          repo.parentsOf(coto.id),
          coto => AppMsg.FocusCoto(coto.id),
          true
        )
      }.flatten,
      header()(
        PartsCoto.divAttributes(coto),
        PartsCoto.addressRemoteAuthor(coto)
      ),
      div(className := "body")(
        PartsCoto.divContent(coto)
      ),
      PartsCoto.articleFooter(coto),
      div(className := "padding-bottom")(
        Option.when(options.showItoTraversalParts) {
          PartsCoto.divOpenDetailsButton(
            coto,
            coto => AppMsg.FocusCoto(coto.id)
          )
        }.flatten
      )
    )
  }

  private def sectionRepostHeader(coto: Coto)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(coto.repostOfId.isDefined) {
      val repo = context.repo
      section(className := "repost-header")(
        Option.when(!repo.nodes.isSelf(coto.postedById)) {
          reposter(coto, repo.nodes)
        },
        materialSymbol("repeat", "repost-icon"),
        // Display the cotonomas to which the coto has been reposted
        // when the current location is a node home (no cotonoma is focused).
        Option.when(repo.cotonomas.focusedId.isEmpty) {
          repostedIn(coto, repo.cotonomas)
        },
        Option.when(context.repo.nodes.canEdit(coto)) {
          toolButton(
            classes = "delete-repost",
            tip = Some("Undo repost"),
            tipPlacement = "right",
            symbol = "close",
            onClick = e => {
              e.stopPropagation()
              dispatch(
                Modal.Msg.OpenModal(
                  Modal.Confirm(
                    "Are you sure you want to undo the repost?",
                    Root.Msg.DeleteCoto(coto.id)
                  )
                )
              )
            }
          )
        }
      )
    }

  private def repostedIn(
      coto: Coto,
      cotonomas: Cotonomas
  )(using dispatch: Into[AppMsg] => Unit): Option[ReactElement] =
    coto.postedInId.flatMap(cotonomas.get).map(cotonoma =>
      a(
        className := "reposted-in",
        onClick := ((e) => {
          e.preventDefault()
          dispatch(AppMsg.FocusCotonoma(cotonoma))
        })
      )(cotonoma.name)
    )

  private def reposter(coto: Coto, nodes: Nodes)(using
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    address(className := "reposter")(
      nodes.get(coto.postedById).map(PartsNode.spanNode)
    )
}
