package cotoami.components

import slinky.core._
import slinky.core.annotations.react
import slinky.web.html._

@react object ToolButton {
  case class Props(
      classes: String = "",
      tip: String,
      tipPlacement: String = "bottom",
      disabled: Boolean = false,
      symbol: String
  )

  val component = FunctionalComponent[Props] { props =>
    button(
      className := s"default tool ${props.classes}",
      data - "tooltip" := props.tip,
      data - "placement" := props.tipPlacement,
      disabled := props.disabled
    )(
      materialSymbol(props.symbol)
    )
  }
}
