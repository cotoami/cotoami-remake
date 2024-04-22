package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg, SelectNode}
import cotoami.components.materialSymbol

object AppHeader {

  def apply(
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    header(
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
          section(className := "location")(
            a(
              className := "node-home",
              title := node.name,
              onClick := ((e) => {
                e.preventDefault()
                dispatch(SelectNode(node.id))
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
}
