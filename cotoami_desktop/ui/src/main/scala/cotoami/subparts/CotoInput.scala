package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.backend.{Cotonoma, Node}

object CotoInput {

  case class Model(form: Form = CotoForm())

  sealed trait Form
  case class CotoForm(content: String = "") extends Form
  case class CotonomaForm(name: String = "") extends Form

  def view(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "coto-input")("hello")
}
