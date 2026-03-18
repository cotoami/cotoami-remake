package cotoami.subparts.modeless

import cotoami.Model

object ModelessDialogOrder {

  enum Action {
    case Focus, Close
  }

  def apply(
      model: Model,
      dialogId: String,
      action: Option[Action]
  ): Model =
    action match {
      case Some(Action.Focus) => model.focusModelessDialog(dialogId)
      case Some(Action.Close) => model.closeModelessDialog(dialogId)
      case None               => model
    }
}
