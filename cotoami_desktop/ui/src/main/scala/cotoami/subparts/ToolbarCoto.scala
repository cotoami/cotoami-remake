package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.toolButton
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Ito}
import cotoami.repository.Root
import cotoami.subparts.Modal

object ToolbarCoto {

  def apply(
      coto: Coto
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    if (coto.isRepost) return None

    val repo = context.repo
    val buttons = Seq(
      Option.when(
        repo.cotos.anySelected &&
          !repo.cotos.isSelecting(coto.id) &&
          repo.canEditItos
      ) {
        toolButton(
          classes = "connect",
          symbol = Ito.ConnectIconName,
          tip = Some(context.i18n.text.Connect),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch(Modal.Msg.OpenModal(Modal.Connect(coto.id)))
          }
        )
      },
      Option.when(repo.canPin(coto.id)) {
        toolButton(
          classes = "pin-coto",
          symbol = "push_pin",
          tip = Some(context.i18n.text.Pin),
          tipPlacement = "left",
          disabled = repo.beingPinned(coto.id),
          onClick = e => {
            e.stopPropagation()
            dispatch(AppMsg.Pin(coto.id))
          }
        )
      },
      Option.when(repo.nodes.canEdit(coto)) {
        toolButton(
          classes = "edit-coto",
          symbol = "edit",
          tip = Some(context.i18n.text.Edit),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch((Modal.Msg.OpenModal.apply _).tupled(Modal.EditCoto(coto)))
          }
        )
      },
      Option.when(repo.nodes.canEditItosIn(coto.nodeId)) {
        toolButton(
          classes = "add-sub-coto",
          symbol = "add",
          tip = Some(context.i18n.text.WriteSubcoto),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch(
              Modal.Msg.OpenModal(Modal.Subcoto(coto.id, None, repo))
            )
          }
        )
      },
      Option.when(repo.canRepost(coto.id)) {
        toolButton(
          classes = "repost-coto",
          symbol = Coto.RepostIconName,
          tip = Some("Repost"),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            Modal.Repost(coto, repo) match {
              case Some(modal) => dispatch(Modal.Msg.OpenModal(modal))
              case None        => () // should be unreachable
            }
          }
        )
      },
      Option.when(repo.nodes.canPromote(coto)) {
        toolButton(
          classes = "promote-to-cotonoma",
          symbol = "drive_folder_upload",
          tip = Some("Promote to Cotonoma"),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch((Modal.Msg.OpenModal.apply _).tupled(Modal.Promote(coto)))
          }
        )
      },
      Option.when(repo.nodes.canDelete(coto) && !coto.isCotonoma) {
        toolButton(
          classes = "delete-coto",
          symbol = "delete",
          tip = Some(context.i18n.text.Delete),
          tipPlacement = "left",
          onClick = e => {
            e.stopPropagation()
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  if (repo.nodes.isOperating(coto.postedById))
                    context.i18n.text.ConfirmDeleteCoto
                  else
                    context.i18n.text.ConfirmDeleteOthersCoto(
                      repo.nodes.get(coto.postedById).map(PartsNode.spanNode)
                    ),
                  Root.Msg.DeleteCoto(coto.id)
                )
              )
            )
          }
        )
      },
      Some(
        if (repo.cotos.isSelecting(coto.id))
          toolButton(
            classes = "select-check-box",
            symbol = "check_box",
            tip = Some("Deselect"),
            tipPlacement = "left",
            onClick = e => {
              e.stopPropagation()
              dispatch(AppMsg.Deselect(coto.id))
            }
          )
        else
          toolButton(
            classes = "select-check-box",
            symbol = "check_box_outline_blank",
            tip = Some("Select"),
            tipPlacement = "left",
            onClick = e => {
              e.stopPropagation()
              dispatch(AppMsg.Select(coto.id))
            }
          )
      )
    ).flatten

    Option.when(!buttons.isEmpty) {
      section(className := "coto-toolbar")(buttons: _*)
    }
  }
}
