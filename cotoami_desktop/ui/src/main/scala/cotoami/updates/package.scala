package cotoami

import scala.util.chaining._

import fui.Cmd
import cotoami.Model
import cotoami.models.UiState

package object updates {

  def addCmd[Model](
      pair: (Model, Cmd[Msg]),
      createCmd: Model => Cmd[Msg]
  ): (Model, Cmd[Msg]) = {
    val (model, cmd) = pair
    (model, cmd ++ createCmd(model))
  }

  def uiState(update: UiState => UiState, model: Model): (Model, Cmd.One[Msg]) =
    model.uiState
      .map(update(_).pipe { case state =>
        (model.copy(uiState = Some(state)), state.save)
      })
      .getOrElse((model, Cmd.none))
}
