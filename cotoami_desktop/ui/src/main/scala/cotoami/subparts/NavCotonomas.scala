package cotoami.subparts

import scala.util.chaining._

import scala.scalajs.js
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{log_debug, log_error, log_info, Context, Msg => AppMsg}
import cotoami.models.{Cotonoma, Id, Node, ParentStatus, ServerNode}
import cotoami.repositories.Cotonomas
import cotoami.backend.{ErrorJson, Paginated, ServerNodeBackend}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  ScrollArea
}

object NavCotonomas {
  final val PaneName = "NavCotonomas"
  final val DefaultWidth = 230

  case class Model(
      loadingRecent: Boolean = false,
      loadingSubs: Boolean = false,
      togglingSync: Boolean = false
  ) {
    def fetchRecent()(implicit context: Context): (Model, Cmd[AppMsg]) =
      (
        copy(loadingRecent = true),
        context.domain.fetchRecentCotonomas(0)
          .map(Msg.toApp(Msg.RecentFetched))
      )

    def fetchMoreRecent()(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (loadingRecent)
        (this, Cmd.none)
      else
        (
          copy(loadingRecent = true),
          context.domain.fetchMoreRecentCotonomas
            .map(Msg.toApp(Msg.RecentFetched))
        )

    def fetchSubs()(implicit context: Context): (Model, Cmd[AppMsg]) =
      (
        copy(loadingSubs = true),
        context.domain.fetchSubCotonomas(0)
          .map(Msg.toApp(Msg.SubsFetched))
      )

    def fetchMoreSubs()(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (loadingSubs)
        (this, Cmd.none)
      else
        (
          copy(loadingSubs = true),
          context.domain.fetchMoreSubCotonomas
            .map(Msg.toApp(Msg.SubsFetched))
        )
  }

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.NavCotonomasMsg(this)
  }
  object Msg {
    def toApp[T](tagger: T => Msg): (T => AppMsg) =
      tagger andThen AppMsg.NavCotonomasMsg

    case object FetchMoreRecent extends Msg
    case class RecentFetched(result: Either[ErrorJson, Paginated[Cotonoma, _]])
        extends Msg
    case object FetchMoreSubs extends Msg
    case class SubsFetched(result: Either[ErrorJson, Paginated[Cotonoma, _]])
        extends Msg
    case class SetSyncDisabled(id: Id[Node], disable: Boolean) extends Msg
    case class SyncToggled(result: Either[ErrorJson, ServerNode]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotonomas, Seq[Cmd[AppMsg]]) = {
    val default = (model, context.domain.cotonomas, Seq.empty)
    msg match {
      case Msg.FetchMoreRecent =>
        model.fetchMoreRecent().pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = Seq(cmd))
        }

      case Msg.RecentFetched(Right(page)) =>
        default.copy(
          _1 = model.copy(loadingRecent = false),
          _2 = context.domain.cotonomas.appendPageOfRecent(page),
          _3 = Seq(log_info(s"Recent cotonomas fetched.", Some(page.debug)))
        )

      case Msg.RecentFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(loadingRecent = false),
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch recent cotonomas."))
        )

      case Msg.FetchMoreSubs =>
        model.fetchMoreSubs().pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = Seq(cmd))
        }

      case Msg.SubsFetched(Right(page)) =>
        default.copy(
          _1 = model.copy(loadingSubs = false),
          _2 = context.domain.cotonomas.appendPageOfSubs(page),
          _3 = Seq(log_info(s"Sub cotonomas fetched.", Some(page.debug)))
        )

      case Msg.SubsFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(loadingSubs = false),
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch sub cotonomas."))
        )

      case Msg.SetSyncDisabled(id, disable) =>
        default.copy(
          _1 = model.copy(togglingSync = true),
          _3 = Seq(
            ServerNodeBackend.update(id, Some(disable), None)
              .map(Msg.toApp(Msg.SyncToggled(_)))
          )
        )

      case Msg.SyncToggled(result) =>
        default.copy(
          _1 = model.copy(togglingSync = false),
          _3 = Seq(
            result match {
              case Right(server) =>
                log_debug(
                  "Parent sync disabled.",
                  Some(server.disabled.toString())
                )
              case Left(e) =>
                log_error(
                  "Failed to disable parent sync.",
                  Some(js.JSON.stringify(e))
                )
            }
          )
        )
    }
  }

  def apply(
      model: Model,
      currentNode: Node
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val domain = context.domain
    val recentCotonomas = context.domain.recentCotonomasWithoutRoot
    nav(className := "cotonomas header-and-body")(
      header()(
        if (domain.cotonomas.focused.isEmpty) {
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
        domain.nodes.focused.map(sectionNodeTools(_, model))
      ),
      section(className := "cotonomas body")(
        ScrollArea(
          onScrollToBottom = Some(() => dispatch(Msg.FetchMoreRecent.toApp))
        )(
          domain.cotonomas.focused.map(sectionCurrent(_, model)),
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
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val domain = context.domain
    val status = domain.nodes.parentStatus(node.id)
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
                  dispatch(Msg.SetSyncDisabled(node.id, !syncDisabled).toApp)
                )
              )
            ),
            span(className := "separator")()
          )

        }),
        toolButton(
          symbol = "settings",
          tip = "Node settings",
          classes = "settings",
          onClick = _ =>
            dispatch(
              Modal.Msg.OpenModal(Modal.NodeProfile(node.id)).toApp
            )
        ),
        Option.when(
          !domain.nodes.operatingRemote &&
            domain.nodes.asChildOf(node.id)
              .map(_.asOwner).getOrElse(false)
        ) {
          toolButton(
            symbol = "switch_account",
            tip = "Operate as",
            classes = "operate",
            onClick = _ =>
              dispatch(
                Modal.Msg.OpenModal(
                  Modal.OperateAs(domain.nodes.operating.get, node)
                ).toApp
              )
          )
        }
      )
    )
  }

  private def sectionCurrent(
      focusedCotonoma: Cotonoma,
      model: Model
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val domain = context.domain
    val superCotonomas = domain.superCotonomasWithoutRoot
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
            domain.cotonomas.subs.map(liCotonoma(_)) ++
              Option.when(
                domain.cotonomas.subIds.nextPageIndex.isDefined
              )(
                li(key := "more-button")(
                  button(
                    className := "more-sub-cotonomas default",
                    onClick := (_ => dispatch(Msg.FetchMoreSubs.toApp))
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
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    section(className := "recent")(
      h2()("Recent"),
      ul()(cotonomas.map(liCotonoma): _*)
    )

  private def liCotonoma(
      cotonoma: Cotonoma
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    li(
      className := optionalClasses(
        Seq(("focused", context.domain.cotonomas.isFocusing(cotonoma.id)))
      ),
      key := cotonoma.id.uuid
    )(
      if (context.domain.cotonomas.isFocusing(cotonoma.id)) {
        span(className := "cotonoma")(cotonomaLabel(cotonoma))
      } else {
        a(
          className := "cotonoma",
          title := cotonoma.name,
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
      context.domain.nodes.get(cotonoma.nodeId).map(imgNode(_)),
      cotonoma.name
    )
}
