package marubinotto

import scala.scalajs.js
import org.scalajs.dom

import marubinotto.react._
import marubinotto.react.ReactNode
import marubinotto.react.Hooks._
import marubinotto.react.SyntheticMouseEvent
import marubinotto.react.html._

package object components {

  def materialSymbol(name: String, classNames: String = ""): ReactNode =
    span(className := s"material-symbols ${classNames}")(name)

  def materialSymbolFilled(
      name: String,
      classNames: String = ""
  ): ReactNode =
    span(className := s"material-symbols font-fill ${classNames}")(name)

  def toolButton(
      symbol: String,
      tip: Option[String] = None,
      tipPlacement: String = "bottom",
      classes: String = "",
      disabled: Boolean = false,
      onClick: SyntheticMouseEvent[?] => Unit = (_ => ())
  ): ReactNode =
    button(
      className := s"default tool ${classes}",
      data - "tooltip" := tip,
      data - "placement" := tipPlacement,
      marubinotto.react.html.disabled := disabled,
      marubinotto.react.html.onClick := onClick,
      onMouseOver := (_.stopPropagation())
    )(
      materialSymbol(symbol)
    )

  def shiftToolButton(
      symbol: String,
      tip: String,
      shiftTip: Option[String] = None,
      tipPlacement: String = "bottom",
      classes: String = "",
      disabled: Boolean = false,
      onClick: SyntheticMouseEvent[?] => Unit = (_ => ())
  ): ReactNode =
    ShiftToolButton.component(
      ShiftToolButton.Props(
        symbol = symbol,
        tip = tip,
        shiftTip = shiftTip,
        tipPlacement = tipPlacement,
        classes = classes,
        disabled = disabled,
        onClick = onClick
      )
    )

  enum CollapseDirection {
    case ToLeft
    case ToRight
  }

  def paneToggle(
      onFoldClick: () => Unit,
      onUnfoldClick: () => Unit,
      direction: CollapseDirection = CollapseDirection.ToLeft
  ): ReactNode =
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

  private object ShiftToolButton {
    case class Props(
        symbol: String,
        tip: String,
        shiftTip: Option[String],
        tipPlacement: String,
        classes: String,
        disabled: Boolean,
        onClick: SyntheticMouseEvent[?] => Unit
    )

    val component = FunctionalComponent[Props] { props =>
      val (hovered, setHovered) = useState(false)
      val (shiftPressed, setShiftPressed) = useState(false)

      val handleKeyDown: js.Function1[dom.KeyboardEvent, Unit] =
        (e: dom.KeyboardEvent) => setShiftPressed.update(_ => e.shiftKey)
      val handleKeyUp: js.Function1[dom.KeyboardEvent, Unit] =
        (e: dom.KeyboardEvent) => setShiftPressed.update(_ => e.shiftKey)

      useEffect(
        () => {
          if (hovered) {
            dom.window.addEventListener("keydown", handleKeyDown)
            dom.window.addEventListener("keyup", handleKeyUp)
          }

          () => {
            dom.window.removeEventListener("keydown", handleKeyDown)
            dom.window.removeEventListener("keyup", handleKeyUp)
          }
        },
        Seq(hovered)
      )

      button(
        className := s"default tool ${props.classes}",
        data - "tooltip" := props.shiftTip.filter(_ => hovered && shiftPressed).getOrElse(props.tip),
        data - "placement" := props.tipPlacement,
        marubinotto.react.html.disabled := props.disabled,
        marubinotto.react.html.onClick := props.onClick,
        onMouseEnter := (e => {
          setHovered.update(_ => true)
          setShiftPressed.update(_ => e.shiftKey)
        }),
        onMouseMove := (e => setShiftPressed.update(_ => e.shiftKey)),
        onMouseLeave := (_ => {
          setHovered.update(_ => false)
          setShiftPressed.update(_ => false)
        }),
        onMouseOver := (_.stopPropagation())
      )(
        materialSymbol(props.symbol)
      )
    }
  }
}
