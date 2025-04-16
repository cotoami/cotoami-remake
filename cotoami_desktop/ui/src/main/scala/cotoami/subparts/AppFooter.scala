package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.materialSymbol
import cotoami.{Into, Model, Msg => AppMsg}

object AppFooter {

  def apply(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    footer(
      div(className := "browser-nav")(
        div(className := "path")(model.path)
      ),
      model.messages
        .lastEntry
        .map(entry =>
          div(className := s"log-peek ${entry.category.name}")(
            button(
              className := "open-log-view default",
              onClick := ((e) => dispatch(ViewSystemMessages.Msg.Toggle))
            )(
              materialSymbol(entry.category.icon),
              entry.message
            )
          )
        )
    )
}
