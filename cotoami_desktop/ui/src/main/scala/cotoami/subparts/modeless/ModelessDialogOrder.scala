package cotoami.subparts.modeless

import cotoami.Model

object ModelessDialogOrder {

  enum Action {
    case Focus, Close
  }

  def apply(
      model: Model,
      dialogId: ModelessDialogId,
      action: Option[Action]
  ): Model =
    action match {
      case Some(Action.Focus) => model.copy(modeless = model.modeless.focusDialog(dialogId))
      case Some(Action.Close) => model.copy(modeless = model.modeless.closeDialog(dialogId))
      case None               => model
    }
}
