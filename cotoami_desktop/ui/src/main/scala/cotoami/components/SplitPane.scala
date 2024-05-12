package cotoami.components

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{React, ReactElement, SetStateHookCallback}
import slinky.core.facade.Hooks._
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

@react object SplitPane {
  case class Props(
      vertical: Boolean, // true: "vertical", false: "horizontal"
      initialPrimarySize: Int,
      resizable: Boolean,
      className: Option[String],
      onResizeStart: Option[() => Unit],
      onResizeEnd: Option[() => Unit],
      onPrimarySizeChanged: Option[Int => Unit],
      children: ReactElement*
  )

  case class Context(
      vertical: Boolean,
      primarySize: Int,
      setPrimarySize: SetStateHookCallback[Int]
  )
  val splitPaneContext = React.createContext[Context](null)

  val component = FunctionalComponent[Props] { props =>
    val (primarySize, setPrimarySize) = useState(props.initialPrimarySize)
    val (moving, setMoving) = useState(false)

    val splitPaneRef = React.createRef[html.Div]
    val separatorPos = useRef(Double.NaN)

    // To allow the callbacks to refer up-to-date state
    // https://stackoverflow.com/a/60643670
    val primarySizeRef = useRef(primarySize)
    primarySizeRef.current = primarySize

    val onMouseDownOnSeparator =
      (e: SyntheticMouseEvent[dom.HTMLDivElement]) => {
        if (props.resizable) {
          setMoving(true)
          if (props.vertical) {
            separatorPos.current = e.clientX
          } else {
            separatorPos.current = e.clientY
          }
          props.onResizeStart.map(_())
        }
      }

    // Define the following event listeners as js.Function1 to avoid implicit
    // conversion from scala.Function1 to js.Function1 when passing them to
    // addEventListener and removeEventListener, which won't work as expected
    // because they become different references as a result of conversion.

    val onMouseMove: js.Function1[dom.MouseEvent, Unit] =
      (e: dom.MouseEvent) => {
        if (!separatorPos.current.isNaN()) {
          // calculate the changed primary size from the position of the mouse cursor
          val cursorPos = if (props.vertical) {
            e.clientX
          } else {
            e.clientY
          }

          val moved = (cursorPos - separatorPos.current).toInt
          var newSize = primarySizeRef.current + moved
          separatorPos.current = cursorPos

          // keep it from resizing beyond the borders of the SplitPane
          if (splitPaneRef.current != null) {
            val splitPaneSize = if (props.vertical) {
              splitPaneRef.current.clientWidth
            } else {
              splitPaneRef.current.clientHeight
            }
            newSize = newSize.max(0).min(splitPaneSize)
          }

          setPrimarySize(newSize)
        }
      }

    val onMouseUp: js.Function1[dom.MouseEvent, Unit] = (e: dom.MouseEvent) => {
      setMoving(false)
      if (!separatorPos.current.isNaN()) {
        props.onPrimarySizeChanged.map(_(primarySizeRef.current))
        props.onResizeEnd.map(_())
        separatorPos.current = Double.NaN
      }
    }

    useEffect(
      () => {
        dom.document.addEventListener("mousemove", onMouseMove)
        dom.document.addEventListener("mouseup", onMouseUp)

        () => {
          dom.document.removeEventListener("mousemove", onMouseMove)
          dom.document.removeEventListener("mouseup", onMouseUp)
        }
      },
      Seq.empty
    )

    div(
      className := optionalClasses(
        Seq(
          ("split-pane", true),
          ("vertical", props.vertical),
          ("horizontal", !props.vertical),
          ("being-resized", moving),
          (props.className.getOrElse(""), props.className.nonEmpty)
        )
      ),
      ref := splitPaneRef
    )(
      splitPaneContext.Provider(value =
        Context(props.vertical, primarySize, setPrimarySize)
      )(
        props.children(0),
        div(
          className := optionalClasses(
            Seq(
              ("separator", true),
              ("moving", moving),
              ("movable", props.resizable)
            )
          ),
          onMouseDown := (onMouseDownOnSeparator(_))
        )(
          div(className := "separator-inner")
        ),
        props.children(1)
      )
    )
  }

  @react object Primary {
    case class Props(
        className: Option[String],
        onClick: Option[() => Unit],
        children: ReactElement*
    )

    val component = FunctionalComponent[Props] { props =>
      val primaryRef = React.createRef[html.Div]
      val Context(vertical, primarySize, setPrimarySize) =
        useContext(splitPaneContext)

      useEffect(
        () => {
          if (vertical) {
            primaryRef.current.style.width = s"${primarySize}px"
          } else {
            primaryRef.current.style.height = s"${primarySize}px"
          }
        },
        Seq(primarySize)
      )

      div(
        className := optionalClasses(
          Seq(
            ("split-pane-primary", true),
            (props.className.getOrElse(""), props.className.nonEmpty)
          )
        ),
        onClick := props.onClick,
        ref := primaryRef
      )(
        props.children: _*
      )
    }
  }

  @react object Secondary {
    case class Props(
        className: Option[String],
        onClick: Option[() => Unit],
        children: ReactElement*
    )

    val component = FunctionalComponent[Props] { props =>
      div(
        className := optionalClasses(
          Seq(
            ("split-pane-secondary", true),
            (props.className.getOrElse(""), props.className.nonEmpty)
          )
        ),
        onClick := props.onClick
      )(props.children: _*)
    }
  }
}
