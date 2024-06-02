package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.CloseModal

object ModalAddNode {

  case class Model(
      nodeUrl: String = "",
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
        model.systemError.map(e => div(className := "system-error")(e)),
        div(className := "body")(
          section(className := "introduction")(
            """
            You can incorporate another database node into your database.
            Once incorporated, it will sync with the original node 
            in real-time as long as you are online.
            """
          ),
          sectionConnect(model, dispatch)
        )
      )
    )

  private def sectionConnect(
      model: Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    section(className := "connect")(
      h2()("Connect"),
      form()(
        //
      )
    )
}
