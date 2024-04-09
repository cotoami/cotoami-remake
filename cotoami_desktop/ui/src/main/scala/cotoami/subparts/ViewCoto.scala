package cotoami.subparts

import scala.scalajs.js.Dynamic.{literal => jso}

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.components.{Markdown, RehypePlugin}
import cotoami.backend.Coto

object ViewCoto {

  def author(
      model: Model,
      coto: Coto
  ): ReactElement =
    address(className := "author")(
      model.nodes.get(coto.postedById).map(node =>
        Fragment(
          nodeImg(node),
          node.name
        )
      )
    )

  def otherCotonomas(
      model: Model,
      coto: Coto,
      dispatch: Msg => Unit
  ): ReactElement =
    ul(className := "other-cotonomas")(
      coto.postedInIds
        .filter(id => !model.isRoot(id) && !model.cotonomas.isSelecting(id))
        .map(model.cotonomas.get)
        .flatten
        .reverse
        .map(cotonoma =>
          li()(
            a(
              className := "also-posted-in",
              onClick := ((e) => {
                e.preventDefault()
                dispatch(cotoami.SelectCotonoma(cotonoma.id))
              })
            )(cotonoma.name)
          )
        )
    )

  def content(
      model: Model,
      coto: Coto,
      dispatch: Msg => Unit
  ): ReactElement =
    div(className := "content")(
      model.cotonomas.asCotonoma(coto).map(cotonoma =>
        section(className := "cotonoma-content")(
          a(
            className := "cotonoma",
            title := cotonoma.name,
            onClick := ((e) => {
              e.preventDefault()
              dispatch(cotoami.SelectCotonoma(cotonoma.id))
            })
          )(
            model.nodes.get(cotonoma.nodeId).map(nodeImg),
            cotonoma.name
          )
        )
      ).getOrElse(
        section(className := "text-content")(
          Markdown(rehypePlugins =
            Seq((RehypePlugin.externalLinks, jso(target = "_blank")))
          )(coto.content)
        )
      )
    )
}
