package cotoami.subparts

import scala.scalajs.js
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
  RehypePlugin,
  RemarkPlugin,
  ToolButton
}
import cotoami.backend.{Coto, CotoContent, Link}
import cotoami.repositories.{Domain, Nodes}

object ViewCoto {

  def addressAuthor(
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

  def divClassifiedAs(
      coto: Coto,
      domain: Domain,
      dispatch: Msg => Unit
  ): ReactElement =
    div(className := "classified-as")(
      ulOtherCotonomas(coto, domain, dispatch),
      Option.when(domain.pinned(coto.id)) {
        div(className := "pinned")(materialSymbol("push_pin"))
      }
    )

  private def ulOtherCotonomas(
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

  def divContent(
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
            content = sectionCotoContent(coto)
          ): ReactElement
        }).getOrElse(sectionCotoContent(coto))
      )
    )

  def divWaitingPostContent(
      post: FormCoto.WaitingPost,
      domain: Domain
  ): ReactElement =
    div(className := "content")(
      post.nameAsCotonoma.map(name =>
        section(className := "cotonoma-content")(
          span(className := "cotonoma")(
            domain.nodes.get(post.postedIn.nodeId).map(nodeImg),
            name
          )
        )
      ).getOrElse(
        post.summary.map(summary => {
          CollapsibleContent(
            summary = summary,
            content = sectionCotoContent(post),
            opened = true
          ): ReactElement
        }).getOrElse(sectionCotoContent(post))
      )
    )

  @react object CollapsibleContent {
    case class Props(
        summary: String,
        content: ReactElement,
        opened: Boolean = false
    )

    val component = FunctionalComponent[Props] { props =>
      val (opened, setOpened) = useState(props.opened)

      div(className := "collapsible-content")(
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
              ("details", true),
              ("open", opened)
            )
          )
        )(props.content)
      )
    }
  }

  private def sectionCotoContent(content: CotoContent): ReactElement =
    section(className := "text-content")(
      Markdown(
        remarkPlugins = Seq(RemarkPlugin.breaks),
        rehypePlugins =
          Seq(js.Tuple2(RehypePlugin.externalLinks, jso(target = "_blank")))
      )(content.content)
    )

  def ulParents(
      parents: Seq[(Coto, Link)],
      dispatch: Msg => Unit
  ): Option[ReactElement] =
    Option.when(!parents.isEmpty) {
      ul(className := "parents")(
        parents.map { case (parent, link) =>
          li(key := link.id.uuid)(
            button(
              className := "parent default",
              onClick := (_ => dispatch(Msg.OpenTraversal(parent.id)))
            )(parent.abbreviate)
          )
        }
      )
    }

  def divLinksTraversal(
      coto: Coto,
      tipPlacement: String,
      dispatch: Msg => Unit
  ): Option[ReactElement] =
    Option.when(coto.outgoingLinks > 0) {
      div(className := "links")(
        ToolButton(
          classes = "open-traversal",
          tip = "Links",
          tipPlacement = tipPlacement,
          symbol = "view_headline",
          onClick = (() => dispatch(Msg.OpenTraversal(coto.id)))
        )
      )
    }
}
