package cotoami.subparts

import scala.scalajs.js
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{log_debug, log_error, Context, Msg => AppMsg}
import cotoami.backend.{Commands, Cotonoma, ErrorJson, Id, Node}
import cotoami.repositories.{Cotonomas, ParentStatus}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  ScrollArea,
  ToolButton
}

object NavCotonomas {
  final val PaneName = "NavCotonomas"
  final val DefaultWidth = 230

  case class Model(togglingSync: Boolean = false)

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.NavCotonomasMsg(this)
  }
  object Msg {
    def toApp[T](tagger: T => Msg): (T => AppMsg) =
      tagger andThen AppMsg.NavCotonomasMsg

    case class SetSyncDisabled(id: Id[Node], disable: Boolean) extends Msg
    case class SyncToggled(result: Either[ErrorJson, Null]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.SetSyncDisabled(id, disable) =>
        (model.copy(togglingSync = true), Seq(setSyncDisabled(id, disable)))

      case Msg.SyncToggled(result) =>
        (
          model.copy(togglingSync = false),
          Seq(
            result match {
              case Right(_) =>
                log_debug("Parent sync disabled.", None)
              case Left(e) =>
                log_error(
                  "Failed to disable parent sync.",
                  Some(js.JSON.stringify(e))
                )
            }
          )
        )
    }

  private def setSyncDisabled(
      id: Id[Node],
      disable: Boolean
  ): Cmd[AppMsg] =
    Commands
      .send(Commands.UpdateServerNode(id, Some(disable), None))
      .map(Msg.toApp(Msg.SyncToggled(_)))

  def apply(
      model: Model,
      currentNode: Node,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
    val domain = context.domain
    val cotonomas = domain.cotonomas
    nav(className := "cotonomas header-and-body")(
      header()(
        if (cotonomas.selected.isEmpty) {
          div(className := "cotonoma home selected")(
            materialSymbol("home"),
            currentNode.name
          )
        } else {
          a(
            className := "cotonoma home",
            title := s"${currentNode.name} home",
            onClick := ((e) => {
              e.preventDefault()
              dispatch(AppMsg.DeselectCotonoma)
            })
          )(
            materialSymbol("home"),
            currentNode.name
          )
        },
        domain.nodes.selected.map(
          sectionNodeTools(_, model, dispatch)
        )
      ),
      section(className := "cotonomas body")(
        ScrollArea(
          scrollableElementId = None,
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => dispatch(Cotonomas.Msg.FetchMoreRecent.toApp)
        )(
          cotonomas.selected.map(
            sectionCurrent(_, dispatch)
          ),
          Option.when(!domain.recentCotonomas.isEmpty)(
            sectionRecent(domain.recentCotonomas, dispatch)
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
      model: Model,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
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
        ToolButton(
          classes = "settings",
          tip = "Node settings",
          symbol = "settings",
          onClick = () =>
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
          ToolButton(
            classes = "operate",
            tip = "Operate as",
            symbol = "switch_account",
            onClick = () =>
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
      selectedCotonoma: Cotonoma,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
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
            domain.superCotonomas.map(
              liCotonoma(_, dispatch)
            ): _*
          )
        ),
        li(key := "current", className := "current-cotonoma cotonoma selected")(
          cotonomaLabel(selectedCotonoma)
        ),
        li(key := "sub")(
          ul(className := "sub-cotonomas")(
            domain.cotonomas.subs.map(
              liCotonoma(_, dispatch)
            ) ++ Option.when(
              domain.cotonomas.subIds.nextPageIndex.isDefined
            )(
              li(key := "more-button")(
                button(
                  className := "more-sub-cotonomas default",
                  onClick := ((e) =>
                    dispatch(
                      Cotonomas.Msg.FetchMoreSubs(selectedCotonoma.id).toApp
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
      cotonomas: Seq[Cotonoma],
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    section(className := "recent")(
      h2()("Recent"),
      ul()(cotonomas.map(liCotonoma(_, dispatch)): _*)
    )

  private def liCotonoma(
      cotonoma: Cotonoma,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    li(
      className := optionalClasses(
        Seq(("selected", context.domain.cotonomas.isSelecting(cotonoma.id)))
      ),
      key := cotonoma.id.uuid
    )(
      if (context.domain.cotonomas.isSelecting(cotonoma.id)) {
        span(className := "cotonoma")(cotonomaLabel(cotonoma))
      } else {
        a(
          className := "cotonoma",
          title := cotonoma.name,
          onClick := ((e) => {
            e.preventDefault()
            dispatch(AppMsg.SelectCotonoma(cotonoma))
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
