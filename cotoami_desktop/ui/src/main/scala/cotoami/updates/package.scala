package cotoami

import org.scalajs.dom.URL

import fui.Cmd
import cotoami.Model
import cotoami.models.{UiState, WaitingPosts}
import cotoami.subparts.SectionTraversals

package object updates {
  def url(url: URL, model: Model): Model =
    model.copy(
      url = url,
      waitingPosts = WaitingPosts(),
      traversals = SectionTraversals.Model()
    )

  def uiState(update: UiState => UiState, model: Model): (Model, Cmd.One[Msg]) =
    model.uiState
      .map(update(_) match {
        case state => (model.copy(uiState = Some(state)), state.save)
      })
      .getOrElse((model, Cmd.none))
}
