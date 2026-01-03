package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Cotonoma, Node, Page}
import cotoami.repository.Cotonomas
import cotoami.backend.ErrorJson

object NavCotonomas {
  final val PaneName = "NavCotonomas"
  final val DefaultWidth = 200

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      loadingRecent: Boolean = false,
      loadingSubs: Boolean = false
  ) {
    def fetchRecent(implicit context: Context): (Model, Cmd.One[AppMsg]) =
      (
        copy(loadingRecent = true),
        context.repo.fetchRecentCotonomas(0)
          .map(Msg.RecentFetched(_).into)
      )

    def fetchMoreRecent(implicit
        context: Context
    ): (Model, Cmd.One[AppMsg]) =
      if (loadingRecent)
        (this, Cmd.none)
      else
        context.repo.fetchMoreRecentCotonomas
          .map(cmd =>
            (
              copy(loadingRecent = true),
              cmd.map(Msg.RecentFetched(_).into)
            )
          )
          .getOrElse((this, Cmd.none))

    def fetchSubs(implicit context: Context): (Model, Cmd.One[AppMsg]) =
      (
        copy(loadingSubs = true),
        context.repo.fetchSubCotonomas(0)
          .map(Msg.SubsFetched(_).into)
      )

    def fetchMoreSubs(implicit
        context: Context
    ): (Model, Cmd.One[AppMsg]) =
      if (loadingSubs)
        (this, Cmd.none)
      else
        context.repo.fetchMoreSubCotonomas
          .map(cmd =>
            (
              copy(loadingSubs = true),
              cmd.map(Msg.SubsFetched(_).into)
            )
          )
          .getOrElse((this, Cmd.none))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.NavCotonomasMsg(this)
  }

  object Msg {
    case object FetchMoreRecent extends Msg
    case class RecentFetched(result: Either[ErrorJson, Page[Cotonoma]])
        extends Msg
    case object FetchMoreSubs extends Msg
    case class SubsFetched(result: Either[ErrorJson, Page[Cotonoma]])
        extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotonomas, Cmd[AppMsg]) = {
    val default = (model, context.repo.cotonomas, Cmd.none)
    msg match {
      case Msg.FetchMoreRecent =>
        model.fetchMoreRecent.pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.RecentFetched(Right(page)) =>
        default.copy(
          _1 = model.copy(loadingRecent = false),
          _2 = context.repo.cotonomas.appendPageOfRecent(page)
        )

      case Msg.RecentFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(loadingRecent = false),
          _3 = cotoami.error("Couldn't fetch recent cotonomas.", e)
        )

      case Msg.FetchMoreSubs =>
        model.fetchMoreSubs.pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = cmd)
        }

      case Msg.SubsFetched(Right(page)) =>
        default.copy(
          _1 = model.copy(loadingSubs = false),
          _2 = context.repo.cotonomas.appendPageOfSubs(page)
        )

      case Msg.SubsFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(loadingSubs = false),
          _3 = cotoami.error("Couldn't fetch sub cotonomas.", e)
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model, nodeTools: SectionNodeTools.Model)(
      currentNode: Node
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    val recentCotonomas = context.repo.recentCotonomas
    nav(className := "cotonomas header-and-body fill")(
      header()(
        if (repo.cotonomas.focused.isEmpty) {
          div(className := "cotonoma home focused")(
            materialSymbol("home"),
            currentNode.name
          )
        } else {
          a(
            className := "cotonoma home",
            title := s"${currentNode.name} home",
            onClick := ((e) => {
              e.preventDefault()
              dispatch(AppMsg.UnfocusCotonoma)
            })
          )(
            materialSymbol("home"),
            currentNode.name
          )
        },
        repo.nodes.focused.map(SectionNodeTools(_, nodeTools))
      ),
      section(className := "cotonomas body")(
        ScrollArea(
          onScrollToBottom = Some(() => dispatch(Msg.FetchMoreRecent))
        )(
          repo.cotonomas.focused.map(sectionCurrent(_, model)),
          Option.when(!recentCotonomas.isEmpty)(
            sectionRecent(recentCotonomas)
          ),
          div(
            className := "more",
            aria - "busy" := model.loadingRecent.toString()
          )()
        )
      )
    )
  }

  private def sectionCurrent(
      focusedCotonoma: Cotonoma,
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    val superCotonomas = repo.superCotonomas
    section(className := "current")(
      h2()(context.i18n.text.NavCotonomas_current),
      ul(
        className := optionalClasses(
          Seq(
            ("has-super-cotonomas", superCotonomas.size > 0)
          )
        )
      )(
        li(key := "super")(
          ul(className := "super-cotonomas")(
            superCotonomas.map(liCotonoma): _*
          )
        ),
        li(key := "current", className := "current-cotonoma cotonoma focused")(
          PartsCotonoma.cotonomaLabel(focusedCotonoma)
        ),
        li(key := "sub")(
          ul(className := "sub-cotonomas")(
            repo.cotonomas.subs.map(liCotonoma(_)) ++
              Option.when(
                repo.cotonomas.subIds.nextPageIndex.isDefined
              )(
                li(key := "more-button")(
                  button(
                    className := "more-sub-cotonomas default",
                    onClick := (_ => dispatch(Msg.FetchMoreSubs))
                  )(
                    materialSymbol("more_horiz")
                  )
                )
              ) ++ Option.when(model.loadingSubs)(
                li(
                  key := "more-loading",
                  className := "more",
                  aria - "busy" := "true"
                )()
              )
          )
        )
      )
    )
  }

  private def sectionRecent(
      cotonomas: Seq[Cotonoma]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "recent")(
      h2()(context.i18n.text.NavCotonomas_recent),
      ul()(cotonomas.map(liCotonoma): _*)
    )

  private def liCotonoma(
      cotonoma: Cotonoma
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    li(
      className := optionalClasses(
        Seq(
          ("focused", context.repo.cotonomas.isFocusing(cotonoma.id)),
          ("being-deleted", context.repo.beingDeleted(cotonoma.cotoId))
        )
      ),
      key := cotonoma.id.uuid
    )(
      if (context.repo.cotonomas.isFocusing(cotonoma.id)) {
        span(className := "cotonoma")(PartsCotonoma.cotonomaLabel(cotonoma))
      } else {
        a(
          className := "cotonoma",
          title := cotonoma.name,
          onClick := ((e) => {
            e.preventDefault()
            dispatch(AppMsg.FocusCotonoma(cotonoma))
          })
        )(
          PartsCotonoma.cotonomaLabel(cotonoma)
        )
      }
    )
}
