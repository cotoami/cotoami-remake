package cotoami

import slinky.core._
import slinky.core.annotations.react
import slinky.web.html._

@react object SplitPane {
  case class Props(className: String)

  val component = FunctionalComponent[Props] { props =>
    div(className := props.className)("hello")
  }
}
