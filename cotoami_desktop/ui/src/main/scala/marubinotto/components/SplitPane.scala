package marubinotto.components

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{React, ReactElement, SetStateHookCallback}
import slinky.core.facade.Hooks._
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import marubinotto.optionalClasses
import marubinotto.Action

@react object SplitPane {
  case class Props(
      vertical: Boolean, // true: "vertical", false: "horizontal"
      reverse: Boolean = false,
      initialPrimarySize: Int,
      resizable: Boolean = true,
      resize: Action[Int] = Action.default,
      className: Option[String] = None,
      onResizeStart: Option[() => Unit] = None,
      onResizeEnd: Option[() => Unit] = None,
      onPrimarySizeChanged: Option[Int => Unit] = None,
      primary: Primary.Props,
      secondary: Secondary.Props
  )

  val PaneClassPrefix = "split-pane-"

  case class Context(
      vertical: Boolean,
      reverse: Boolean,
      primarySize: Int,
      setPrimarySize: SetStateHookCallback[Int]
  )
  val splitPaneContext = React.createContext[Context](null)

  val component = FunctionalComponent[Props] { props =>
    val (primarySize, setPrimarySize) = useState(props.initialPrimarySize)
    val (moving, setMoving) = useState(false)

    val splitPaneRef = useRef[html.Div](null)
    val separatorPosRef = useRef(Double.NaN)

    // To allow the callbacks to access the up-to-date state.primarySize
    // https://stackoverflow.com/a/60643670
    val primarySizeRef = useRef(primarySize)
    primarySizeRef.current = primarySize

    val onMouseDownOnSeparator =
      useCallback(
        (e: SyntheticMouseEvent[dom.HTMLDivElement]) => {
          if (props.resizable) {
            setMoving(true)
            if (props.vertical) {
              separatorPosRef.current = e.clientX
            } else {
              separatorPosRef.current = e.clientY
            }
            props.onResizeStart.map(_())
          }
        },
        Seq(props.resizable, props.vertical, props.onResizeStart)
      )

    // Define the following event listeners as js.Function1 to avoid implicit
    // conversion from scala.Function1 to js.Function1 when passing them to
    // addEventListener and removeEventListener, which won't work as expected
    // because they become different references as a result of conversion.

    val onMouseMove: js.Function1[dom.MouseEvent, Unit] =
      useCallback(
        (e: dom.MouseEvent) => {
          if (!separatorPosRef.current.isNaN()) {
            // calculate the changed primary size from the position of the mouse cursor
            val cursorPos = if (props.vertical) {
              e.clientX
            } else {
              e.clientY
            }

            val moved = (cursorPos - separatorPosRef.current).toInt
            var newSize =
              if (props.reverse)
                primarySizeRef.current - moved
              else
                primarySizeRef.current + moved

            separatorPosRef.current = cursorPos

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
        },
        Seq(props.vertical, props.reverse)
      )

    val onMouseUp: js.Function1[dom.MouseEvent, Unit] = useCallback(
      (e: dom.MouseEvent) => {
        setMoving(false)
        if (!separatorPosRef.current.isNaN()) {
          props.onPrimarySizeChanged.map(_(primarySizeRef.current))
          props.onResizeEnd.map(_())
          separatorPosRef.current = Double.NaN
        }
      },
      Seq(props.onPrimarySizeChanged, props.onResizeEnd)
    )

    useEffect(
      () => {
        dom.document.addEventListener("mousemove", onMouseMove)
        dom.document.addEventListener("mouseup", onMouseUp)

        () => {
          dom.document.removeEventListener("mousemove", onMouseMove)
          dom.document.removeEventListener("mouseup", onMouseUp)
        }
      },
      Seq(props.vertical, props.reverse)
    )

    // resize
    useEffect(
      () => {
        if (props.resizable) {
          props.resize.parameter.foreach { newSize =>
            setPrimarySize(newSize)
            props.onPrimarySizeChanged.map(_(newSize))
          }
        }
      },
      Seq(props.resize.triggered)
    )

    div(
      className := optionalClasses(
        Seq(
          ("split-pane", true),
          ("vertical", props.vertical),
          ("horizontal", !props.vertical),
          ("reverse", props.reverse),
          ("being-resized", moving),
          (props.className.getOrElse(""), props.className.nonEmpty)
        )
      ),
      ref := splitPaneRef
    )(
      splitPaneContext.Provider(value =
        Context(props.vertical, props.reverse, primarySize, setPrimarySize)
      )(
        Primary.component(props.primary),
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
        Secondary.component(props.secondary)
      )
    )
  }

  @react object Primary {
    case class Props(
        className: Option[String] = None,
        onClick: Option[() => Unit] = None
    )(children: ReactElement*) {
      def getChildren: Seq[ReactElement] = children
    }

    val component = FunctionalComponent[Props] { props =>
      val primaryRef = useRef[html.Div](null)
      val Context(vertical, reverse, primarySize, setPrimarySize) =
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
            (s"${PaneClassPrefix}primary", true),
            (positionalClass(vertical, reverse), true),
            (props.className.getOrElse(""), props.className.nonEmpty)
          )
        ),
        onClick := props.onClick,
        ref := primaryRef
      )(
        props.getChildren: _*
      )
    }

    private def positionalClass(
        vertical: Boolean,
        reverse: Boolean
    ): String =
      (vertical, reverse) match {
        case (true, false)  => s"${PaneClassPrefix}left"
        case (true, true)   => s"${PaneClassPrefix}right"
        case (false, false) => s"${PaneClassPrefix}top"
        case (false, true)  => s"${PaneClassPrefix}bottom"
      }
  }

  @react object Secondary {
    case class Props(
        className: Option[String] = None,
        onClick: Option[() => Unit] = None
    )(children: ReactElement*) {
      def getChildren: Seq[ReactElement] = children
    }

    val component = FunctionalComponent[Props] { props =>
      val Context(vertical, reverse, primarySize, setPrimarySize) =
        useContext(splitPaneContext)

      div(
        className := optionalClasses(
          Seq(
            (s"${PaneClassPrefix}secondary", true),
            (positionalClass(vertical, reverse), true),
            (props.className.getOrElse(""), props.className.nonEmpty)
          )
        ),
        onClick := props.onClick
      )(props.getChildren: _*)
    }

    private def positionalClass(
        vertical: Boolean,
        reverse: Boolean
    ): String =
      (vertical, reverse) match {
        case (true, false)  => s"${PaneClassPrefix}right"
        case (true, true)   => s"${PaneClassPrefix}left"
        case (false, false) => s"${PaneClassPrefix}bottom"
        case (false, true)  => s"${PaneClassPrefix}top"
      }
  }
}
