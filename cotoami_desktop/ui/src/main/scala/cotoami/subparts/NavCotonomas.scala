package cotoami.subparts

import scala.util.chaining._

import scala.scalajs.js
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import com.softwaremill.quicklens._

import fui.Cmd
import cotoami.{log_debug, log_error, log_info, Context, Msg => AppMsg}
import cotoami.backend.{
  Cotonoma,
  ErrorJson,
  Id,
  Node,
  Paginated,
  PaginatedIds,
  ServerNode
}
import cotoami.repositories.{Cotonomas, ParentStatus}
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
      cotonomaIds: PaginatedIds[Cotonoma] = PaginatedIds(),
      loading: Boolean = false,
      togglingSync: Boolean = false
  ) {
    def appendPage(page: Paginated[Cotonoma, _]): Model =
      this
        .modify(_.cotonomaIds).using(_.appendPage(page))
        .modify(_.loading).setTo(false)

    def fetchMore()(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (this.loading) {
        (this, Cmd.none)
      } else {
        this.cotonomaIds.nextPageIndex match {
          case Some(nextIndex) =>
            (
              this.copy(loading = true),
              context.domain.fetchRecentCotonomas(nextIndex)
            )
          case None => (this, Cmd.none)
        }
      }
  }

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.NavCotonomasMsg(this)
  }
  object Msg {
    def toApp[T](tagger: T => Msg): (T => AppMsg) =
      tagger andThen AppMsg.NavCotonomasMsg

    case object FetchMore extends Msg
    case class Fetched(result: Either[ErrorJson, Paginated[Cotonoma, _]])
        extends Msg
    case class SetSyncDisabled(id: Id[Node], disable: Boolean) extends Msg
    case class SyncToggled(result: Either[ErrorJson, ServerNode]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cotonomas, Seq[Cmd[AppMsg]]) = {
    val default = (model, context.domain.cotonomas, Seq.empty)
    msg match {
      case Msg.FetchMore =>
        model.fetchMore().pipe { case (model, cmd) =>
          default.copy(_1 = model, _3 = Seq(cmd))
        }

      case Msg.Fetched(Right(page)) =>
        default.copy(
          _1 = model.appendPage(page),
          _2 = context.domain.cotonomas.putAll(page.rows),
          _3 = Seq(log_info(s"Recent cotonomas fetched.", Some(page.debug)))
        )

      case Msg.Fetched(Left(e)) =>
        default.copy(
          _1 = model.copy(loading = false),
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch recent cotonomas."))
        )

      case Msg.SetSyncDisabled(id, disable) =>
        default.copy(
          _1 = model.copy(togglingSync = true),
          _3 = Seq(
            ServerNode.update(id, Some(disable), None)
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
    val cotonomas = domain.cotonomas
    nav(className := "cotonomas header-and-body")(
      header()(
        if (cotonomas.focused.isEmpty) {
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
          onScrollToBottom =
            Some(() => dispatch(Cotonomas.Msg.FetchMoreRecent.toApp))
        )(
          cotonomas.focused.map(sectionCurrent),
          Option.when(!domain.recentCotonomas.isEmpty)(
            sectionRecent(domain.recentCotonomas)
          ),
          div(
            className := "more",
            aria - "busy" := cotonomas.recentLoading.toString()
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
    val statusParts = status.flatMap(parentStatusParts(_))
    section(className := "node-tools")(
      statusParts.map(parts =>
        details(
          className := optionalClasses(
            Seq(
              ("node-status", true),
              (parts.slug, true),
              ("no-message", parts.message.isEmpty)
            )
          )
        )(
          summary()(
            parts.icon,
            span(className := "name")(parts.slug)
          ),
          parts.message.map(p(className := "message")(_))
        )
      ),
      div(className := "tools")(
        status.map(status => {
          val syncDisabled = status == ParentStatus.Disabled
          Fragment(
            span(
              className := "sync-switch",
              data - "tooltip" := (
                if (syncDisabled) "Sync OFF" else "Sync ON"
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
              (Modal.Msg.OpenModal.apply _).tupled(
                Modal.NodeProfile(node)
              ).toApp
            )
        ),
        Option.when(
          !domain.nodes.operatingRemote &&
            domain.nodes.operatingAsChild(node.id)
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
      focusedCotonoma: Cotonoma
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val domain = context.domain
    section(className := "current")(
      h2()("Current"),
      ul(
        className := optionalClasses(
          Seq(
            ("has-super-cotonomas", domain.superCotonomas.size > 0)
          )
        )
      )(
        li(key := "super")(
          ul(className := "super-cotonomas")(
            domain.superCotonomas.map(liCotonoma): _*
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
                    onClick := ((e) =>
                      dispatch(
                        Cotonomas.Msg.FetchMoreSubs(focusedCotonoma.id).toApp
                      )
                    )
                  )(
                    materialSymbol("more_horiz")
                  )
                )
              ) ++ Option.when(domain.cotonomas.subsLoading)(
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
