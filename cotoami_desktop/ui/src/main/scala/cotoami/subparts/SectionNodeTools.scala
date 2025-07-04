package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node, ServerNode}
import cotoami.backend.{ErrorJson, ServerNodeBackend}

object SectionNodeTools {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      togglingSync: Boolean = false
  )

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

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

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      node: Node,
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    val status = repo.nodes.parentStatus(node.id)
    val statusView = status.map(ViewConnectionStatus(_))
    section(className := "node-tools")(
      statusView.map(PartsNode.detailsConnectionStatus),
      div(className := "tools")(
        status.map(status => {
          val syncDisabled = status.disabled
          Fragment(
            span(
              className := "sync-switch",
              data - "tooltip" := (
                if (syncDisabled) context.i18n.text.SectionNodeTools_enableSync
                else context.i18n.text.SectionNodeTools_disableSync
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
          onClick =
            _ => dispatch(Modal.Msg.OpenModal(Modal.NodeProfile(node.id)))
        ),
        PartsNode.buttonSwitchNode(node)
      )
    )
  }
}
