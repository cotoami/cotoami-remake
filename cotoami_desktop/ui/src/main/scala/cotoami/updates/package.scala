package cotoami

import fui.Cmd
import cotoami.Model
import cotoami.models.UiState

package object updates {

  def addCmd[M](
      pair: (M, Cmd[Msg]),
      createCmd: M => Cmd.One[Msg]
  ): (M, Cmd[Msg]) = {
    val (model, cmd) = pair
    (model, cmd ++ createCmd(model))
  }

  def uiState(update: UiState => UiState, model: Model): (Model, Cmd.One[Msg]) =
    model.uiState
      .map(update(_) match {
        case state => (model.copy(uiState = Some(state)), state.save)
      })
      .getOrElse((model, Cmd.none))
}
