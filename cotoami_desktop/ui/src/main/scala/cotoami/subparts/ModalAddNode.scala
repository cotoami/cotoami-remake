package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.CloseModal

object ModalAddNode {

  case class Model(
      serverUrl: String = "",
      password: String = "",
      systemError: Option[String] = None
  )

  def apply(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    dialog(
      className := "add-node",
      open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          button(
            className := "close default",
            onClick := (_ => dispatch(CloseModal))
          ),
          h1()("Add Node")
        ),
        div(className := "body")(
          model.systemError.map(e => div(className := "system-error")(e)),
          div(className := "body-main")(
            //
          )
        )
      )
    )
}
