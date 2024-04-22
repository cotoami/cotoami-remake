package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.components.materialSymbol

object AppFooter {

  def apply(
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    footer(
      div(className := "browser-nav")(
        div(className := "path")(model.path)
      ),
      model.log
        .lastEntry()
        .map(entry =>
          div(className := s"log-peek ${entry.level.name}")(
            button(
              className := "open-log-view default",
              onClick := ((e) => dispatch(cotoami.ToggleLogView))
            )(
              materialSymbol(entry.level.icon),
              entry.message
            )
          )
        )
    )
}
