package cotoami

import org.scalajs.dom.html

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{React, ReactElement, SetStateHookCallback}
import slinky.core.facade.Hooks._
import slinky.web.html._

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

    div(className := s"split-pane ${props.split} ${props.className}")(
      splitPaneContext.Provider(value =
        Context(props.split, primarySize, setPrimarySize)
      )(
        props.children(0),
        div(className := "separator"),
        props.children(1)
      )
    )
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
