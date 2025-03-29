package cotoami.subparts

import scala.util.chaining._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Cotonoma, Id, Node, Page, ParentStatus, ServerNode}
import cotoami.repository.Cotonomas
import cotoami.backend.{ErrorJson, ServerNodeBackend}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  ScrollArea
}

object NavCotonomas {
  final val PaneName = "NavCotonomas"
  final val DefaultWidth = 200

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      loadingRecent: Boolean = false,
      loadingSubs: Boolean = false,
      togglingSync: Boolean = false
  ) {
    def fetchRecent()(implicit context: Context): (Model, Cmd.One[AppMsg]) =
      (
        copy(loadingRecent = true),
        context.repo.fetchRecentCotonomas(0)
          .map(Msg.RecentFetched(_).into)
      )

    def fetchMoreRecent()(implicit
        context: Context
    ): (Model, Cmd.One[AppMsg]) =
      if (loadingRecent)
        (this, Cmd.none)
      else
        (
          copy(loadingRecent = true),
          context.repo.fetchMoreRecentCotonomas
            .map(Msg.RecentFetched(_).into)
        )

    def fetchSubs()(implicit context: Context): (Model, Cmd.One[AppMsg]) =
      (
        copy(loadingSubs = true),
        context.repo.fetchSubCotonomas(0)
          .map(Msg.SubsFetched(_).into)
      )

    def fetchMoreSubs()(implicit
        context: Context
    ): (Model, Cmd.One[AppMsg]) =
      if (loadingSubs)
        (this, Cmd.none)
      else
        (
          copy(loadingSubs = true),
          context.repo.fetchMoreSubCotonomas
            .map(Msg.SubsFetched(_).into)
        )
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
    case class SetSyncDisabled(id: Id[Node], disable: Boolean) extends Msg
    case class SyncToggled(result: Either[ErrorJson, ServerNode]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotonomas, Cmd[AppMsg]) = {
    val default = (model, context.repo.cotonomas, Cmd.none)
    msg match {
      case Msg.FetchMoreRecent =>
        model.fetchMoreRecent().pipe { case (model, cmd) =>
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
        model.fetchMoreSubs().pipe { case (model, cmd) =>
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

      case Msg.SetSyncDisabled(id, disable) =>
        default.copy(
          _1 = model.copy(togglingSync = true),
          _3 = ServerNodeBackend.edit(id, Some(disable), None)
            .map(Msg.SyncToggled(_).into)
        )

      case Msg.SyncToggled(result) =>
        default.copy(
          _1 = model.copy(togglingSync = false),
          _3 = result match {
            case Right(server) => Cmd.none
            case Left(e) => cotoami.error("Failed to disable parent sync.", e)
          }
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model,
      currentNode: Node
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    val recentCotonomas = context.repo.recentCotonomasWithoutRoot
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
        repo.nodes.focused.map(sectionNodeTools(_, model))
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

  private def sectionNodeTools(
      node: Node,
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    val status = repo.nodes.parentStatus(node.id)
    val statusView = status.flatMap(viewParentStatus(_))
    section(className := "node-tools")(
      statusView.map(view =>
        details(
          className := optionalClasses(
            Seq(
              ("node-status", true),
              (view.className, true),
              ("no-message", view.message.isEmpty)
            )
          )
        )(
          summary()(
            view.icon,
            span(className := "name")(view.title)
          ),
          view.message.map(p(className := "message")(_))
        )
      ),
      div(className := "tools")(
        status.map(status => {
          val syncDisabled = status == ParentStatus.Disabled
          Fragment(
            span(
              className := "sync-switch",
              data - "tooltip" := (
                if (syncDisabled) "Connect" else "Disconnect"
              ),
              data - "placement" := "bottom"
            )(
              input(
                `type` := "checkbox",
                role := "switch",
                checked := !syncDisabled,
                disabled := model.togglingSync,
                onChange := (_ =>
                  dispatch(Msg.SetSyncDisabled(node.id, !syncDisabled))
                )
              )
            ),
            span(className := "separator")()
          )

        }),
        toolButton(
          symbol = "settings",
          tip = Some("Node settings"),
          classes = "settings",
          onClick = _ =>
            dispatch(
              (Modal.Msg.OpenModal.apply _).tupled(
                Modal.NodeProfile(node.id, repo.nodes)
              )
            )
        ),
        PartsNode.buttonOperateAs(node)
      )
    )
  }

  private def sectionCurrent(
      focusedCotonoma: Cotonoma,
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    val superCotonomas = repo.superCotonomasWithoutRoot
    section(className := "current")(
      h2()("Current"),
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
          cotonomaLabel(focusedCotonoma)
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
      h2()("Recent"),
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
        span(className := "cotonoma")(cotonomaLabel(cotonoma))
      } else {
        a(
          className := optionalClasses(
            Seq(
              ("cotonoma", true),
              ("highlighted", context.isHighlighting(cotonoma.cotoId))
            )
          ),
          title := cotonoma.name,
          onMouseEnter := (_ => dispatch(AppMsg.Highlight(cotonoma.cotoId))),
          onMouseLeave := (_ => dispatch(AppMsg.Unhighlight)),
          onClick := ((e) => {
            e.preventDefault()
            dispatch(AppMsg.FocusCotonoma(cotonoma))
          })
        )(
          cotonomaLabel(cotonoma)
        )
      }
    )

  private def cotonomaLabel(
      cotonoma: Cotonoma
  )(implicit context: Context): ReactElement =
    Fragment(
      context.repo.nodes.get(cotonoma.nodeId).map(PartsNode.imgNode(_)),
      cotonoma.name
    )
}
