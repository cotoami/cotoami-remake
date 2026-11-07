package cotoami.subparts

import marubinotto.react.ReactNode
import marubinotto.react.html._

import marubinotto.components.materialSymbol
import cotoami.{Into, Model, Msg => AppMsg}

object AppFooter {

  def apply(
      model: Model
  )(using dispatch: Into[AppMsg] => Unit): ReactNode =
    footer(
      div(className := "browser-nav")(
        div(className := "path")(model.path)
      ),
      model.messages
        .lastEntry
        .map(entry =>
          div(className := s"message-peek ${entry.category.name}")(
            button(
              className := "open-messages-view default",
              onClick := (_ => dispatch(ViewMessages.Msg.Toggle))
            )(
              materialSymbol(entry.category.icon),
              entry.message
            )
          )
        )
    )
}
