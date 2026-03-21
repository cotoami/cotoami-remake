package marubinotto

import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.core.facade.Hooks._
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
      onClick: SyntheticMouseEvent[?] => Unit = (_ => ())
  ): ReactElement =
    button(
      className := s"default tool ${classes}",
      data - "tooltip" := tip,
      data - "placement" := tipPlacement,
      slinky.web.html.disabled := disabled,
      slinky.web.html.onClick := onClick,
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
  ): ReactElement =
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
        (e: dom.KeyboardEvent) => setShiftPressed(_ => e.shiftKey)
      val handleKeyUp: js.Function1[dom.KeyboardEvent, Unit] =
        (e: dom.KeyboardEvent) => setShiftPressed(_ => e.shiftKey)

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
        slinky.web.html.disabled := props.disabled,
        slinky.web.html.onClick := props.onClick,
        onMouseEnter := (e => {
          setHovered(_ => true)
          setShiftPressed(_ => e.shiftKey)
        }),
        onMouseMove := (e => setShiftPressed(_ => e.shiftKey)),
        onMouseLeave := (_ => {
          setHovered(_ => false)
          setShiftPressed(_ => false)
        }),
        onMouseOver := (_.stopPropagation())
      )(
        materialSymbol(props.symbol)
      )
    }
  }
}
