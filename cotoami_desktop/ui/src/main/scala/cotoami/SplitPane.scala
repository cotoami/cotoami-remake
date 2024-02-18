package cotoami

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.web.html._

@react object SplitPane {
  case class Props(className: String, children: ReactElement*)

  val component = FunctionalComponent[Props] { props =>
    div(className := "split-pane " + props.className)(
      props.children(0),
      div(className := "separator"),
      props.children(1)
    )
  }

  @react object First {
    case class Props(children: ReactElement*)

    val component = FunctionalComponent[Props] { props =>
      div(className := "split-pane-first")(props.children)
    }
  }

  @react object Second {
    case class Props(children: ReactElement*)

    val component = FunctionalComponent[Props] { props =>
      div(className := "split-pane-second")(props.children)
    }
  }
}
