package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import marubinotto.fui.Cmd
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, PaginatedIds}
import cotoami.repository.Root
import cotoami.backend.{ErrorJson, PaginatedCotos}

object PaneSearch {

  final val PaneName = "PaneSearch"
  final val DefaultWidth = 500

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      queryInput: String = "",

      // To avoid rendering old results unintentionally
      fetchNumber: Int = 0,

      // Search results
      executedQuery: Option[String] = None,
      cotoIds: PaginatedIds[Coto] = PaginatedIds(),

      // State
      imeActive: Boolean = false,
      loading: Boolean = false
  ) {
    def active: Boolean = !queryInput.isBlank()

    def inputQuery(query: String): (Model, Cmd[AppMsg]) =
      if (query.isBlank())
        (
          copy(
            queryInput = query,
            executedQuery = None,
            cotoIds = PaginatedIds(),
            loading = false
          ),
          Cmd.none
        )
      else {
        if (imeActive)
          (copy(queryInput = query), Cmd.none)
        else
          copy(queryInput = query)
            .fetchFirst
            .modify(_._2).using(_.debounce("PaneSearch.query", 200))
      }

    def clear: Model =
      copy(
        queryInput = "",
        fetchNumber = 0,
        executedQuery = None,
        cotoIds = PaginatedIds(),
        loading = false
      )

    def fetchFirst: (Model, Cmd.One[AppMsg]) =
      fetching.pipe { model =>
        (model, fetch(queryInput, 0, model.fetchNumber))
      }

    def fetchMore: (Model, Cmd.One[AppMsg]) =
      if (loading)
        (this, Cmd.none)
      else
        cotoIds.nextPageIndex.map(i =>
          fetching.pipe { model =>
            (model, fetch(queryInput, i, model.fetchNumber))
          }
        ).getOrElse((this, Cmd.none)) // no more

    private def fetching: Model =
      copy(fetchNumber = fetchNumber + 1, loading = true)

    def appendPage(
        cotos: PaginatedCotos,
        query: String,
        fetchNumber: Int
    ): Model =
      this
        .modify(_.cotoIds).using(_.appendPage(cotos.page))
        .modify(_.executedQuery).setTo(Some(query))
        .modify(_.fetchNumber).setTo(fetchNumber)
        .modify(_.loading).setTo(false)

    def cotos(repo: Root): Seq[Coto] =
      cotoIds.order.map(repo.cotos.get).flatten
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.PaneSearchMsg(this)
  }

  object Msg {
    case class QueryInput(query: String) extends Msg
    case object ClearQuery extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case object FetchMore extends Msg
    case class Fetched(
        number: Int,
        query: String,
        result: Either[ErrorJson, PaginatedCotos]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Root, Cmd[AppMsg]) = {
    val default = (model, context.repo, Cmd.none)
    msg match {
      case Msg.QueryInput(query) =>
        model.inputQuery(query).pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.ClearQuery => default.copy(_1 = model.clear)

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

      case Msg.Fetched(number, query, Right(cotos)) =>
        if (number == model.fetchNumber)
          default.copy(
            _1 = model.appendPage(cotos, query, number),
            _2 = context.repo.importFrom(cotos)
          )
        else
          default

      case Msg.Fetched(_, query, Left(e)) =>
        default.copy(
          _1 = model.copy(loading = false),
          _3 = cotoami.error(s"Couldn't search cotos by [${query}].", e)
        )
    }
  }

  private def fetch(
      query: String,
      pageIndex: Double,
      fetchNumber: Int
  ): Cmd.One[AppMsg] =
    if (!query.isBlank())
      PaginatedCotos.search(
        query,
        None,
        None,
        false,
        pageIndex
      ).map(Msg.Fetched(fetchNumber, query, _).into)
    else
      Cmd.none

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "search header-and-body fill")(
      header()(
        span(className := "title")(
          materialSymbol("search"),
          span(className := "query")(model.executedQuery)
        )
      ),
      div(className := "coto-flow body")(
        ScrollArea(
          onScrollToBottom = Some(() => dispatch(Msg.FetchMore))
        )(
          (
            model.cotos(context.repo).map(sectionCoto) :+
              div(
                className := "more",
                aria - "busy" := model.loading.toString()
              )()
          ): _*
        )
      )
    )

  private def sectionCoto(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    section(className := "coto flow-entry")(
      PartsCoto.ulParents(
        repo.parentsOf(coto.id),
        SectionTraversals.Msg.OpenTraversal(_).into
      ),
      PartsCoto.article(coto, dispatch)(
        ToolbarCoto(coto),
        header()(
          PartsCoto.divAttributes(coto),
          Option.when(Some(coto.postedById) != repo.nodes.selfId) {
            PartsCoto.addressAuthor(coto, repo.nodes)
          }
        ),
        div(className := "body")(
          PartsCoto.divContent(coto)
        ),
        PartsCoto.articleFooter(coto)
      ),
      PartsCoto.divDetailsButton(coto)
    )
  }
}
