package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

object SectionTraversals {

  case class Model(
  )

  def apply(model: Model, dispatch: cotoami.Msg => Unit): ReactElement =
    section(className := "traversals")(
    )
}
