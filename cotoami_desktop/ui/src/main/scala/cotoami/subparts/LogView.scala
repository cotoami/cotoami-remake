package cotoami.subparts

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Msg, Log}

object LogView {

  def view(
      log: Log,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "log-view")(
      header(className := "tools")(
        button(
          className := "close-log-view icon",
          onClick := ((e) => dispatch(cotoami.ToggleLogView))
        )(cotoami.icon("close"))
      ),
      div(className := "log-entries")(
        log.entries.map(entry =>
          div(className := s"log-entry ${entry.level.name}")(
            div(className := "level")(cotoami.icon(entry.level.icon)),
            div(className := "content")(
              div(className := "message")(entry.message),
              div(className := "details")(entry.details)
            )
          )
        )
      )
    )
}
