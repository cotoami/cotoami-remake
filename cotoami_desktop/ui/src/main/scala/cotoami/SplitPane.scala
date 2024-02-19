package cotoami

import org.scalajs.dom.html

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{React, ReactElement, SetStateHookCallback}
import slinky.core.facade.Hooks._
import slinky.web.html._

@react object SplitPane {
  case class Props(className: String, firstSize: Int, children: ReactElement*)

  case class Context(firstSize: Int, setFirstSize: SetStateHookCallback[Int])
  val splitPaneContext = React.createContext[Context](null)

  val component = FunctionalComponent[Props] { props =>
    val (firstSize, setFirstSize) = useState(props.firstSize)

    div(className := "split-pane " + props.className)(
      splitPaneContext.Provider(value = Context(firstSize, setFirstSize))(
        props.children(0),
        div(className := "separator"),
        props.children(1)
      )
    )
  }

  @react object First {
    case class Props(children: ReactElement*)

    val component = FunctionalComponent[Props] { props =>
      val firstRef = React.createRef[html.Div]
      val Context(firstSize, setFirstSize) = useContext(splitPaneContext)

      useEffect(
        () => {
          firstRef.current.style.width = s"${firstSize}px"
        },
        Seq(firstSize)
      )

      div(className := "split-pane-first", ref := firstRef)(props.children)
    }
  }

  @react object Second {
    case class Props(children: ReactElement*)

    val component = FunctionalComponent[Props] { props =>
      div(className := "split-pane-second")(props.children)
    }
  }
}
