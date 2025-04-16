package cotoami.subparts

import org.scalajs.dom.html

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import marubinotto.components.materialSymbol

import cotoami.{Into, Msg => AppMsg}
import cotoami.models.SystemMessages

object ViewSystemMessages {

  def apply(messages: SystemMessages)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "log-view")(
      header(className := "tools")(
        button(
          className := "close-log-view default",
          onClick := ((e) => dispatch(AppMsg.ToggleLogView))
        )(materialSymbol("close"))
      ),
      LogEntries(entries = messages.entries)
    )

  @react object LogEntries {
    case class Props(
        entries: Seq[SystemMessages.Entry]
    )

    val component = FunctionalComponent[Props] { props =>
      val bottomRef = useRef[html.Div](null)

      useEffect(() => {
        bottomRef.current.scrollIntoView(false)
      })

      div(className := "log-entries")(
        props.entries.map(entry =>
          div(
            className := s"log-entry ${entry.category.name}",
            key := entry.timestamp.getTime().toString()
          )(
            div(className := "level")(materialSymbol(entry.category.icon)),
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
