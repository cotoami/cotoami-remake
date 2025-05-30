package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
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
  WaitingPost,
  WaitingPosts
}
import cotoami.repository._
import cotoami.backend.{ErrorJson, PaginatedCotos}

object SectionTimeline {

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

      // To avoid rendering old results unintentionally
      fetchNumber: Int = 0,

      // To restore the scroll position when back to timeline
      scrollPos: Option[(Id[Cotonoma], Double)] = None,

      // State
      imeActive: Boolean = false,
      loading: Boolean = false,
      markingAsRead: Boolean = false
  ) {
    def onFocusChange(implicit context: Context): (Model, Cmd.One[AppMsg]) =
      copy(
        cotoIds = PaginatedIds(),
        waitingPosts = WaitingPosts(),
        onlyCotonomas = false,
        queryInput = "",
        fetchNumber = 0,
        loading = false,
        imeActive = false
      ).fetchFirst

    def cotos(implicit context: Context): Seq[Coto] = {
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
      this
        .modify(_.cotoIds).using(cotoIds =>
          if (queryInput.isEmpty)
            cotoIds.prependId(cotoId)
          else
            cotoIds
        )

    def fetchFirst(implicit context: Context): (Model, Cmd.One[AppMsg]) =
      fetching.pipe { model =>
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

    def fetchMore(implicit context: Context): (Model, Cmd.One[AppMsg]) =
      if (loading)
        (this, Cmd.none)
      else
        cotoIds.nextPageIndex.map(i =>
          fetching.pipe { model =>
            (
              model,
              fetchInFocus(
                context.repo,
                onlyCotonomas,
                Some(queryInput),
                i,
                model.fetchNumber
              )
            )
          }
        ).getOrElse((this, Cmd.none)) // no more

    private def fetching: Model =
      copy(fetchNumber = fetchNumber + 1, loading = true)

    def inputQuery(
        query: String
    )(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (imeActive)
        (copy(queryInput = query), Cmd.none)
      else
        copy(queryInput = query).fetchFirst
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionTimelineMsg(this)
  }

  object Msg {
    case class SetOnlyCotonomas(onlyCotonomas: Boolean) extends Msg
    case class QueryInput(query: String) extends Msg
    case object ClearQuery extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case object FetchMore extends Msg
    case class Fetched(number: Int, result: Either[ErrorJson, PaginatedCotos])
        extends Msg
    case class ScrollAreaUnmounted(cotonomaId: Id[Cotonoma], scrollPos: Double)
        extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
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
        model.inputQuery("").pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.ImeCompositionStart =>
        default.copy(_1 = model.copy(imeActive = true))

      case Msg.ImeCompositionEnd =>
        model.fetchFirst.pipe { case (model, cmd) =>
          default.copy(
            _1 = model.copy(imeActive = false),
            _3 = cmd
          )
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
          nodeId,
          cotonomaId,
          onlyCotonomas,
          pageIndex
        )
      case _ =>
        PaginatedCotos.fetchRecent(nodeId, cotonomaId, onlyCotonomas, pageIndex)
    }).map(Msg.Fetched(fetchNumber, _).into)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    val cotos = model.cotos
    context.repo.currentCotonomaId.flatMap(cotonomaId =>
      Option.when(
        !model.queryInput.isBlank || !cotos.isEmpty || !model.waitingPosts.isEmpty
      )(
        sectionTimeline(model, cotos, cotonomaId)
      )
    )
  }

  private def sectionTimeline(
      model: Model,
      cotos: Seq[Coto],
      currentCotonomaId: Id[Cotonoma]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "timeline header-and-body")(
      header(className := "tools")(
        Option.when(context.repo.nodes.anyUnreadPostsInFocus)(
          button(
            className := "mark-as-read contrast outline",
            disabled := model.markingAsRead,
            aria - "busy" := model.markingAsRead.toString()
          )(context.i18n.text.MarkAllAsRead)
        ),
        div(className := "search")(
          input(
            `type` := "search",
            name := "query",
            value := model.queryInput,
            onChange := ((e) => dispatch(Msg.QueryInput(e.target.value))),
            onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
            onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd))
          ),
          Option.when(!model.queryInput.isBlank) {
            button(
              className := "clear default",
              onClick := (_ => dispatch(Msg.ClearQuery))
            )(materialSymbol("close"))
          }
        )
      ),
      div(className := "coto-flow body")(
        ScrollArea(
          setScrollTop = model.getScrollPos(currentCotonomaId),
          onScrollToBottom = Some(() => dispatch(Msg.FetchMore)),
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
              cotos.map(coto =>
                Flipped(key = coto.id.uuid, flipId = coto.id.uuid)(
                  sectionPost(coto, model)
                ): ReactElement
              ) :+
              div(
                className := "more",
                aria - "busy" := model.loading.toString()
              )()): _*
          )
        )
      )
    )

  private def sectionWaitingPost(
      post: WaitingPost
  )(implicit context: Context): ReactElement =
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
      coto: Coto,
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "post flow-entry")(
      repostHeader(coto),
      context.repo.cotos.getOriginal(coto).map(coto =>
        Fragment(
          PartsCoto.ulParents(
            context.repo.parentsOf(coto.id),
            AppMsg.FocusCoto(_)
          ),
          articleCoto(coto),
          PartsCoto.divItosTraversal(coto, "bottom")
        )
      )
    )

  private def articleCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val repo = context.repo
    PartsCoto.article(coto, dispatch)(
      ToolbarCoto(coto),
      header()(
        PartsCoto.divAttributes(coto),
        Option.when(!repo.nodes.isSelf(coto.postedById)) {
          PartsCoto.addressAuthor(coto, repo.nodes)
        }
      ),
      div(className := "body")(
        PartsCoto.divContent(coto)
      ),
      PartsCoto.articleFooter(coto),
      Option.when(context.repo.nodes.unread(coto))(
        materialSymbolFilled("brightness_1", "unread-mark")
      )
    )
  }

  private def repostHeader(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(coto.repostOfId.isDefined) {
      val repo = context.repo
      section(className := "repost-header")(
        materialSymbol("repeat"),
        // Display the cotonomas to which the coto has been reposted
        // when the current location is a node home (no cotonoma is focused).
        Option.when(repo.cotonomas.focusedId.isEmpty) {
          repostedIn(coto, repo.cotonomas)
        },
        Option.when(!repo.nodes.isSelf(coto.postedById)) {
          reposter(coto, repo.nodes)
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
  )(implicit dispatch: Into[AppMsg] => Unit): Option[ReactElement] =
    coto.postedInId.flatMap(cotonomas.get).map(cotonoma =>
      a(
        className := "reposted-in",
        onClick := ((e) => {
          e.preventDefault()
          dispatch(AppMsg.FocusCotonoma(cotonoma))
        })
      )(cotonoma.name)
    )

  private def reposter(coto: Coto, nodes: Nodes)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    address(className := "reposter")(
      nodes.get(coto.postedById).map(PartsNode.spanNode)
    )
}
