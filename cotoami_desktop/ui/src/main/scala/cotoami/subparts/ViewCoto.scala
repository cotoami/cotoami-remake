package cotoami.subparts

import scala.scalajs.js.Dynamic.{literal => jso}

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.components.{Markdown, RehypePlugin}
import cotoami.backend.Coto

object ViewCoto {
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
