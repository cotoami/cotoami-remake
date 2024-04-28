package cotoami.subparts

import scala.scalajs.js.Dynamic.{literal => jso}

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{Fragment, ReactElement}
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.Msg
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  Markdown,
  RehypePlugin
}
import cotoami.backend.Coto
import cotoami.repositories.{Domain, Nodes}

object ViewCoto {

  def author(
      coto: Coto,
      nodes: Nodes
  ): ReactElement =
    address(className := "author")(
      nodes.get(coto.postedById).map(node =>
        Fragment(
          nodeImg(node),
          node.name
        )
      )
    )

  def otherCotonomas(
      coto: Coto,
      domain: Domain,
      dispatch: Msg => Unit
  ): ReactElement =
    ul(className := "other-cotonomas")(
      coto.postedInIds
        .filter(id => !domain.isRoot(id) && !domain.cotonomas.isSelecting(id))
        .map(domain.cotonomas.get)
        .flatten
        .reverse
        .map(cotonoma =>
          li(key := cotonoma.id.uuid)(
            a(
              className := "also-posted-in",
              onClick := ((e) => {
                e.preventDefault()
                dispatch(cotoami.SelectCotonoma(cotonoma.id))
              })
            )(cotonoma.name)
          )
        ): _*
    )

  def content(
      coto: Coto,
      domain: Domain,
      dispatch: Msg => Unit
  ): ReactElement =
    div(className := "content")(
      domain.cotonomas.asCotonoma(coto).map(cotonoma =>
        section(className := "cotonoma-content")(
          a(
            className := "cotonoma",
            title := cotonoma.name,
            onClick := ((e) => {
              e.preventDefault()
              dispatch(cotoami.SelectCotonoma(cotonoma.id))
            })
          )(
            domain.nodes.get(cotonoma.nodeId).map(nodeImg),
            cotonoma.name
          )
        )
      ).getOrElse(
        coto.summary.map(summary => {
          CollapsibleContent(
            summary = summary,
            content = cotoContent(coto)
          ): ReactElement
        }).getOrElse(cotoContent(coto))
      )
    )

  @react object CollapsibleContent {
    case class Props(
        summary: String,
        content: ReactElement
    )

    val component = FunctionalComponent[Props] { props =>
      val (opened, setOpened) = useState(false)

      div(className := "summary-and-content")(
        section(className := "summary")(
          button(
            className := "content-toggle default",
            onClick := (_ => setOpened(!opened))
          )(
            if (opened)
              materialSymbol("keyboard_double_arrow_up")
            else
              materialSymbol("keyboard_double_arrow_down")
          ),
          span(
            className := "summary",
            onClick := (_ => setOpened(!opened))
          )(props.summary)
        ),
        div(
          className := optionalClasses(
            Seq(
              ("collapsible-content", true),
              ("open", opened)
            )
          )
        )(props.content)
      )
    }
  }

  private def cotoContent(coto: Coto): ReactElement =
    section(className := "text-content")(
      Markdown(rehypePlugins =
        Seq((RehypePlugin.externalLinks, jso(target = "_blank")))
      )(coto.content)
    )
}
