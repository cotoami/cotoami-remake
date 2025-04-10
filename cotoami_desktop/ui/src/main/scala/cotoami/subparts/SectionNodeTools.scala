package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node, ServerNode}
import cotoami.backend.{ErrorJson, ServerNodeBackend}

object SectionNodeTools {

  case class Model(
      togglingSync: Boolean = false
  )

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionNodeToolsMsg(this)
  }

  object Msg {
    case class SetSyncDisabled(id: Id[Node], disable: Boolean) extends Msg
    case class SyncToggled(result: Either[ErrorJson, ServerNode]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.SetSyncDisabled(id, disable) =>
        (
          model.copy(togglingSync = true),
          ServerNodeBackend.edit(id, Some(disable), None, None)
            .map(Msg.SyncToggled(_).into)
        )

      case Msg.SyncToggled(result) =>
        (
          model.copy(togglingSync = false),
          result match {
            case Right(server) => Cmd.none
            case Left(e) => cotoami.error("Failed to disable parent sync.", e)
          }
        )
    }

  def apply(
      node: Node,
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    val status = repo.nodes.parentStatus(node.id)
    val statusView = status.flatMap(ViewParentStatus(_))
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
          val syncDisabled = status.disabled
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
          tip = Some("Node Settings"),
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
}
