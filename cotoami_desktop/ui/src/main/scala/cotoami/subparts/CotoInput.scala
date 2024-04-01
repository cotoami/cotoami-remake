package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

object CotoInput {

  case class Model(form: Form = CotoForm())

  sealed trait Form
  case class CotoForm(content: String = "") extends Form
  case class CotonomaForm(name: String = "") extends Form

  def view(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "coto-input")()
}
