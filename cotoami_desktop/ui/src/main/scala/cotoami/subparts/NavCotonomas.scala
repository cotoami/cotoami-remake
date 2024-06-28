package cotoami.subparts

import scala.scalajs.js
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{log_debug, log_error, Msg => AppMsg}
import cotoami.backend.{Commands, Cotonoma, ErrorJson, Id, Node}
import cotoami.repositories.{Cotonomas, Domain, ParentStatus}
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
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement = {
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
          sectionNodeTools(model, _, domain, dispatch)
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
            sectionCurrent(_, domain, dispatch)
          ),
          Option.when(!domain.recentCotonomas.isEmpty)(
            sectionRecent(domain.recentCotonomas, domain, dispatch)
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
      model: Model,
      node: Node,
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement = {
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
                onClick := (_ =>
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
          onClick = (() => ())
        ),
        ToolButton(
          classes = "operate",
          tip = "Operate",
          symbol = "switch_account",
          onClick = (() => ())
        )
      )
    )
  }

  private def sectionCurrent(
      selectedCotonoma: Cotonoma,
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement =
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
              liCotonoma(_, domain, dispatch)
            ): _*
          )
        ),
        li(key := "current", className := "current-cotonoma cotonoma selected")(
          cotonomaLabel(selectedCotonoma, domain)
        ),
        li(key := "sub")(
          ul(className := "sub-cotonomas")(
            domain.cotonomas.subs.map(
              liCotonoma(_, domain, dispatch)
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

  private def sectionRecent(
      cotonomas: Seq[Cotonoma],
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement =
    section(className := "recent")(
      h2()("Recent"),
      ul()(cotonomas.map(liCotonoma(_, domain, dispatch)): _*)
    )

  private def liCotonoma(
      cotonoma: Cotonoma,
      domain: Domain,
      dispatch: AppMsg => Unit
  ): ReactElement =
    li(
      className := optionalClasses(
        Seq(("selected", domain.cotonomas.isSelecting(cotonoma.id)))
      ),
      key := cotonoma.id.uuid
    )(
      if (domain.cotonomas.isSelecting(cotonoma.id)) {
        span(className := "cotonoma")(cotonomaLabel(cotonoma, domain))
      } else {
        a(
          className := "cotonoma",
          title := cotonoma.name,
          onClick := ((e) => {
            e.preventDefault()
            dispatch(AppMsg.SelectCotonoma(cotonoma))
          })
        )(
          cotonomaLabel(cotonoma, domain)
        )
      }
    )

  private def cotonomaLabel(cotonoma: Cotonoma, domain: Domain): ReactElement =
    Fragment(
      domain.nodes.get(cotonoma.nodeId).map(nodeImg),
      cotonoma.name
    )
}
