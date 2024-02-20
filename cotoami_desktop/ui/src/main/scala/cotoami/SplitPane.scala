package cotoami

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
      className: String,
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

    val onMouseDownOnSeparator =
      (e: SyntheticMouseEvent[dom.HTMLDivElement]) => {
        setMoving(true)
        if (props.vertical) {
          separatorPos.current = e.clientX
        } else {
          separatorPos.current = e.clientY
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
          var newSize = primarySize + moved
          separatorPos.current = cursorPos

          // keep it from resizing beyond the borders of the SplitPane
          val splitPaneSize = if (props.vertical) {
            splitPaneRef.current.clientWidth
          } else {
            splitPaneRef.current.clientHeight
          }
          newSize = newSize.max(0).min(splitPaneSize)

          setPrimarySize(newSize)
        }
      }

    val onMouseUp: js.Function1[dom.MouseEvent, Unit] = (e: dom.MouseEvent) => {
      setMoving(false)
      separatorPos.current = Double.NaN
    }

    useEffect(() => {
      dom.document.addEventListener("mousemove", onMouseMove)
      dom.document.addEventListener("mouseup", onMouseUp)

      () => {
        dom.document.removeEventListener("mousemove", onMouseMove)
        dom.document.removeEventListener("mouseup", onMouseUp)
      }
    })

    div(
      className := optionalClasses(
        Seq(
          ("split-pane", true),
          ("vertical", props.vertical),
          ("horizontal", !props.vertical),
          (props.className, true)
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
            Seq(("separator", true), ("moving", moving))
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
    case class Props(children: ReactElement*)

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

      div(className := "split-pane-primary", ref := primaryRef)(props.children)
    }
  }

  @react object Secondary {
    case class Props(children: ReactElement*)

    val component = FunctionalComponent[Props] { props =>
      div(className := "split-pane-secondary")(props.children)
    }
  }
}
