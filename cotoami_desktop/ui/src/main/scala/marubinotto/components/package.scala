package marubinotto

import slinky.core.facade.ReactElement
import slinky.web.SyntheticMouseEvent
import slinky.web.html._

package object components {

  def materialSymbol(name: String, classNames: String = ""): ReactElement =
    span(className := s"material-symbols ${classNames}")(name)

  def materialSymbolFilled(
      name: String,
      classNames: String = ""
  ): ReactElement =
    span(className := s"material-symbols font-fill ${classNames}")(name)

  def toolButton(
      symbol: String,
      tip: Option[String] = None,
      tipPlacement: String = "bottom",
      classes: String = "",
      disabled: Boolean = false,
      onClick: SyntheticMouseEvent[_] => Unit = (_ => ())
  ): ReactElement =
    button(
      className := s"default tool ${classes}",
      data - "tooltip" := tip,
      data - "placement" := tipPlacement,
      slinky.web.html.disabled := disabled,
      slinky.web.html.onClick := onClick
    )(
      materialSymbol(symbol)
    )

  sealed trait CollapseDirection
  object CollapseDirection {
    case object ToLeft extends CollapseDirection
    case object ToRight extends CollapseDirection
  }

  def paneToggle(
      onFoldClick: () => Unit,
      onUnfoldClick: () => Unit,
      direction: CollapseDirection = CollapseDirection.ToLeft
  ): ReactElement =
    div(className := "pane-toggle")(
      button(
        className := "fold default",
        title := "Fold",
        onClick := (_ => onFoldClick())
      )(
        span(className := "material-symbols")(
          direction match {
            case CollapseDirection.ToLeft  => "arrow_left"
            case CollapseDirection.ToRight => "arrow_right"
          }
        )
      ),
      button(
        className := "unfold default",
        title := "Unfold",
        onClick := (_ => onUnfoldClick())
      )(
        span(className := "material-symbols")(
          direction match {
            case CollapseDirection.ToLeft  => "arrow_right"
            case CollapseDirection.ToRight => "arrow_left"
          }
        )
      )
    )
}
