package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg => AppMsg}
import cotoami.components.materialSymbol

object AppHeader {

  def apply(
      model: Model,
      dispatch: AppMsg => Unit
  ): ReactElement =
    header(
      data - "tauri-drag-region" := "default",
      data - "os" := model.systemInfo.map(_.os).getOrElse("")
    )(
      div(
        className := "header-content",
        data - "tauri-drag-region" := "default"
      )(
        button(
          className := "app-info default",
          title := "View app info"
        )(
          img(
            className := "app-icon",
            alt := "Cotoami",
            src := "/images/logo/logomark.svg"
          )
        ),
        model
          .domain
          .location
          .map { case (node, cotonoma) =>
            section(
              className := "location",
              data - "tauri-drag-region" := "default"
            )(
              a(
                className := "node-home",
                title := node.name,
                onClick := ((e) => {
                  e.preventDefault()
                  dispatch(AppMsg.SelectNode(node.id))
                })
              )(nodeImg(node)),
              cotonoma.map(cotonoma =>
                Fragment(
                  materialSymbol("chevron_right", "arrow"),
                  h1(className := "current-cotonoma")(cotonoma.name)
                )
              )
            )
          }
      )
    )
}
