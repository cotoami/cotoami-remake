package cotoami.subparts

import org.scalajs.dom.html

import slinky.core._
import slinky.core.facade.{React, ReactElement}
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.{Log, Msg}
import cotoami.components.material_symbol

object LogView {

  def view(
      log: Log,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "log-view")(
      header(className := "tools")(
        button(
          className := "close-log-view default",
          onClick := ((e) => dispatch(cotoami.ToggleLogView))
        )(material_symbol("close"))
      ),
      LogEntries(entries = log.entries)
    )

  @react object LogEntries {
    case class Props(
        entries: Seq[Log.Entry]
    )

    val component = FunctionalComponent[Props] { props =>
      val bottomRef = React.createRef[html.Div]

      useEffect(() => {
        bottomRef.current.scrollIntoView(false)
      })

      div(className := "log-entries")(
        props.entries.map(entry =>
          div(
            className := s"log-entry ${entry.level.name}",
            key := entry.timestamp.getTime().toString()
          )(
            div(className := "level")(material_symbol(entry.level.icon)),
            div(className := "content")(
              div(className := "message")(entry.message),
              div(className := "details")(entry.details)
            )
          )
        ) :+ div(key := "bottom", ref := bottomRef)(): _*
      )
    }
  }
}
