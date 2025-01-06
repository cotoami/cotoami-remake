package cotoami.subparts

import scala.util.chaining._
import scala.collection.immutable.HashSet
import scala.scalajs.LinkingInfo

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import com.softwaremill.quicklens._

import fui._
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
import cotoami.repositories._
import cotoami.backend.{CotosPage, ErrorJson}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  ScrollArea
}

object SectionTimeline {

  case class Model(
      cotoIds: PaginatedIds[Coto] = PaginatedIds(),
      onlyCotonomas: Boolean = false,
      justPosted: HashSet[Id[Coto]] = HashSet.empty,
      query: String = "",
      // to avoid rendering old results unintentionally
      fetchNumber: Int = 0,
      // to restore the scroll position when back to timeline
      scrollPos: Option[(Id[Cotonoma], Double)] = None,
      loading: Boolean = false,
      imeActive: Boolean = false
  ) {
    def clear: Model =
      copy(
        cotoIds = PaginatedIds(),
        query = "",
        loading = false,
        imeActive = false
      )

    def cotos(domain: Domain): Seq[Coto] = {
      val rootCotoId = domain.currentNodeRootCotonoma.map(_.cotoId)
      cotoIds.order.filter(Some(_) != rootCotoId).map(domain.cotos.get).flatten
    }

    def appendPage(cotos: CotosPage, fetchNumber: Int): Model =
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
          if (query.isEmpty)
            cotoIds.prependId(cotoId)
          else
            cotoIds
        )
        .modify(_.justPosted).using(_ + cotoId)

    def removeFromJustPosted(cotoId: Id[Coto]): Model =
      this.modify(_.justPosted).using(_ - cotoId)

    def fetchFirst(domain: Domain): (Model, Cmd.One[AppMsg]) =
      (
        copy(loading = true),
        fetchInFocus(domain, onlyCotonomas, Some(query), 0, fetchNumber + 1)
      )

    def fetchMore(domain: Domain): (Model, Cmd.One[AppMsg]) =
      if (loading)
        (this, Cmd.none)
      else
        cotoIds.nextPageIndex.map(i =>
          (
            copy(loading = true),
            fetchInFocus(domain, onlyCotonomas, Some(query), i, fetchNumber + 1)
          )
        ).getOrElse((this, Cmd.none)) // no more

    def inputQuery(query: String, domain: Domain): (Model, Cmd[AppMsg]) =
      if (imeActive)
        (copy(query = query), Cmd.none)
      else
        copy(query = query).fetchFirst(domain)
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionTimelineMsg(this)
  }

  object Msg {
    case class SetOnlyCotonomas(onlyCotonomas: Boolean) extends Msg
    case object FetchMore extends Msg
    case class Fetched(number: Int, result: Either[ErrorJson, CotosPage])
        extends Msg
    case object ClearQuery extends Msg
    case class QueryInput(query: String) extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case class ScrollAreaUnmounted(cotonomaId: Id[Cotonoma], scrollPos: Double)
        extends Msg
    case class PostAnimationEnd(cotoId: Id[Coto]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Domain, Cmd[AppMsg]) = {
    if (LinkingInfo.developmentMode) {
      println(s"SectionTimeline.update: ${msg.getClass()}")
    }

    val default = (model, context.domain, Cmd.none)
    msg match {
      case Msg.SetOnlyCotonomas(onlyCotonomas) =>
        model
          .modify(_.cotoIds).setTo(PaginatedIds())
          .modify(_.onlyCotonomas).setTo(onlyCotonomas)
          .fetchFirst(context.domain)
          .pipe { case (model, cmd) =>
            default.copy(_1 = model, _3 = cmd)
          }

      case Msg.FetchMore =>
        model.fetchMore(context.domain).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.Fetched(number, Right(cotos)) =>
        if (number > model.fetchNumber)
          default.copy(
            _1 = model.appendPage(cotos, number),
            _2 = context.domain.importFrom(cotos)
          )
        else
          default

      case Msg.Fetched(_, Left(e)) =>
        default.copy(
          _1 = model.copy(loading = false),
          _3 = cotoami.error("Couldn't fetch timeline cotos.", e)
        )

      case Msg.ClearQuery =>
        model.inputQuery("", context.domain).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.QueryInput(query) =>
        model.inputQuery(query, context.domain).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.ImeCompositionStart =>
        default.copy(_1 = model.copy(imeActive = true))

      case Msg.ImeCompositionEnd =>
        default.copy(
          _1 = model.copy(imeActive = false),
          _3 = fetchInFocus(
            context.domain,
            model.onlyCotonomas,
            Some(model.query),
            0,
            model.fetchNumber + 1
          )
        )

      case Msg.ScrollAreaUnmounted(cotonomaId, scrollPos) =>
        default.copy(_1 = model.saveScrollPos(cotonomaId, scrollPos))

      case Msg.PostAnimationEnd(cotoId) =>
        default.copy(_1 = model.removeFromJustPosted(cotoId))
    }
  }

  private def fetchInFocus(
      domain: Domain,
      onlyCotonomas: Boolean,
      query: Option[String],
      pageIndex: Double,
      fetchNumber: Int
  ): Cmd.One[AppMsg] =
    fetch(
      domain.nodes.focusedId,
      domain.cotonomas.focusedId,
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
        CotosPage.search(query, nodeId, cotonomaId, onlyCotonomas, pageIndex)
      case _ =>
        CotosPage.fetchRecent(nodeId, cotonomaId, onlyCotonomas, pageIndex)
    }).map(Msg.Fetched(fetchNumber, _).into)

  def apply(
      model: Model,
      waitingPosts: WaitingPosts
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    val cotos = model.cotos(context.domain)
    context.domain.currentCotonomaId.flatMap(cotonomaId =>
      Option.when(
        !model.query.isBlank || !cotos.isEmpty || !waitingPosts.isEmpty
      )(
        sectionTimeline(model, cotos, waitingPosts, cotonomaId)
      )
    )
  }

  private def sectionTimeline(
      model: Model,
      cotos: Seq[Coto],
      waitingPosts: WaitingPosts,
      currentCotonomaId: Id[Cotonoma]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
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
            onChange := ((e) => dispatch(Msg.QueryInput(e.target.value))),
            onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
            onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd))
          ),
          Option.when(!model.query.isBlank) {
            button(
              className := "clear default",
              onClick := (_ => dispatch(Msg.ClearQuery))
            )(materialSymbol("close"))
          }
        )
      ),
      div(className := "posts body")(
        ScrollArea(
          initialScrollTop = model.getScrollPos(currentCotonomaId),
          onScrollToBottom = Some(() => dispatch(Msg.FetchMore)),
          onUnmounted = Some(scrollTop =>
            dispatch(
              Msg.ScrollAreaUnmounted(currentCotonomaId, scrollTop)
            )
          )
        )(
          (waitingPosts.posts.map(sectionWaitingPost(_)) ++
            cotos.map(sectionPost(_, model)) :+
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
      coto: Coto,
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("post", true),
          ("just-posted", model.justPosted.contains(coto.id))
        )
      ),
      key := coto.id.uuid,
      onAnimationEnd := (e => {
        if (e.animationName == "just-posted") {
          dispatch(Msg.PostAnimationEnd(coto.id).into)
        }
      })
    )(
      repostHeader(coto),
      context.domain.cotos.getOriginal(coto).map(coto =>
        Fragment(
          ViewCoto.ulParents(
            context.domain.parentsOf(coto.id),
            AppMsg.FocusCoto(_)
          ),
          articleCoto(coto),
          ViewCoto.divLinksTraversal(coto, "bottom")
        )
      )
    )

  private def articleCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val domain = context.domain
    article(
      className := optionalClasses(ViewCoto.commonArticleClasses(coto)),
      onClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
    )(
      ToolbarCoto(coto),
      header()(
        ViewCoto.divAttributes(coto),
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
      dispatch: Into[AppMsg] => Unit
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
        },
        Option.when(context.domain.nodes.canEdit(coto)) {
          toolButton(
            classes = "delete-repost",
            tip = "Delete this repost",
            tipPlacement = "right",
            symbol = "close",
            onClick = e => {
              e.stopPropagation()
              dispatch(
                Modal.Msg.OpenModal(
                  Modal.Confirm(
                    "Are you sure you want to delete the repost?",
                    Domain.Msg.DeleteCoto(coto.id)
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

  private def reposter(coto: Coto, nodes: Nodes): ReactElement =
    address(className := "reposter")(
      nodes.get(coto.postedById).map(spanNode)
    )
}
