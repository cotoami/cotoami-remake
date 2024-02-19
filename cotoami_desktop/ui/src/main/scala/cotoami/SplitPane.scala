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
      split: String, // "vertical" or "horizontal"
      initialPrimarySize: Int,
      className: String,
      children: ReactElement*
  )

  case class Context(
      split: String,
      primarySize: Int,
      setPrimarySize: SetStateHookCallback[Int]
  )
  val splitPaneContext = React.createContext[Context](null)

  val component = FunctionalComponent[Props] { props =>
    val (primarySize, setPrimarySize) = useState(props.initialPrimarySize)
    val (moving, setMoving) = useState(false)

    val splitPaneRef = React.createRef[html.Div]
    val separatorPosition = useRef(Double.NaN)

    val onMouseDownOnSeparator =
      (e: SyntheticMouseEvent[dom.HTMLDivElement]) => {
        setMoving(true)
        props.split match {
          case "vertical" =>
            separatorPosition.current = e.clientX
          case "horizontal" =>
            separatorPosition.current = e.clientY
        }
      }

    // Define the following event listeners as js.Function1 to avoid implicit
    // conversion from scala.Function1 to js.Function1 when passing them to
    // addEventListener and removeEventListener, which won't work as expected
    // because they become different references as a result of conversion.

    val onMouseMove: js.Function1[dom.MouseEvent, Unit] =
      (e: dom.MouseEvent) => {
        if (!separatorPosition.current.isNaN()) {
          val pointer = props.split match {
            case "vertical"   => e.clientX
            case "horizontal" => e.clientY
          }
          val moved = (pointer - separatorPosition.current).toInt
          val newSize = primarySize + moved
          separatorPosition.current = pointer
          setPrimarySize(newSize)
        }
      }

    val onMouseUp: js.Function1[dom.MouseEvent, Unit] = (e: dom.MouseEvent) => {
      setMoving(false)
      separatorPosition.current = Double.NaN
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
      className := s"split-pane ${props.split} ${props.className}",
      ref := splitPaneRef
    )(
      splitPaneContext.Provider(value =
        Context(props.split, primarySize, setPrimarySize)
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

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  @react object Primary {
    case class Props(children: ReactElement*)

    val component = FunctionalComponent[Props] { props =>
      val primaryRef = React.createRef[html.Div]
      val Context(split, primarySize, setPrimarySize) =
        useContext(splitPaneContext)

      useEffect(
        () => {
          split match {
            case "vertical" =>
              primaryRef.current.style.width = s"${primarySize}px"
            case "horizontal" =>
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
