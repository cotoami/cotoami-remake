package cotoami.subparts

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{Fragment, ReactElement}
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.{Context, Msg => AppMsg}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  Markdown,
  RehypePlugin,
  RemarkPlugin
}
import cotoami.backend.{Coto, CotoContent, Link}
import cotoami.repositories.Nodes
import cotoami.models.WaitingPost

object ViewCoto {

  def addressAuthor(
      coto: Coto,
      nodes: Nodes
  ): ReactElement =
    address(className := "author")(
      nodes.get(coto.postedById).map(spanNode)
    )

  def divClassifiedAs(
      coto: Coto,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    div(className := "classified-as")(
      ulOtherCotonomas(coto, dispatch),
      Option.when(context.domain.pinned(coto.id)) {
        div(className := "pinned")(materialSymbol("push_pin"))
      }
    )

  private def ulOtherCotonomas(
      coto: Coto,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    ul(className := "other-cotonomas")(
      coto.postedInIds
        .filter(id =>
          !context.domain.isCurrentRoot(id) &&
            !context.domain.cotonomas.isSelecting(id)
        )
        .map(context.domain.cotonomas.get)
        .flatten
        .reverse
        .map(cotonoma =>
          li(key := cotonoma.id.uuid)(
            a(
              className := "also-posted-in",
              onClick := ((e) => {
                e.preventDefault()
                dispatch(AppMsg.SelectCotonoma(cotonoma))
              })
            )(cotonoma.name)
          )
        ): _*
    )

  def divContent(
      coto: Coto,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    div(className := "content")(
      context.domain.cotonomas.asCotonoma(coto).map(cotonoma =>
        section(className := "cotonoma-content")(
          a(
            className := "cotonoma",
            title := cotonoma.name,
            onClick := ((e) => {
              e.preventDefault()
              dispatch(AppMsg.SelectCotonoma(cotonoma))
            })
          )(
            context.domain.nodes.get(cotonoma.nodeId).map(imgNode(_)),
            cotonoma.name
          )
        )
      ).getOrElse(sectionCotoContent(coto))
    )

  def divWaitingPostContent(
      post: WaitingPost
  )(implicit context: Context): ReactElement =
    div(className := "content")(
      post.nameAsCotonoma.map(name =>
        section(className := "cotonoma-content")(
          span(className := "cotonoma")(
            context.domain.nodes.get(post.postedIn.nodeId).map(imgNode(_)),
            name
          )
        )
      ).getOrElse(sectionCotoContent(post, true))
    )

  private def sectionCotoContent(
      cotoContent: CotoContent,
      collapsibleContentOpened: Boolean = false
  ): ReactElement =
    section(className := "coto-content")(
      cotoContent.summary.map(summary => {
        CollapsibleContent(
          summary = summary,
          details = sectionCotoContentDetails(cotoContent),
          opened = collapsibleContentOpened
        ): ReactElement
      }).getOrElse(sectionCotoContentDetails(cotoContent))
    )

  @react object CollapsibleContent {
    case class Props(
        summary: String,
        details: ReactElement,
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
        )(props.details)
      )
    }
  }

  def sectionCotoContentDetails(content: CotoContent): ReactElement =
    Fragment(
      section(className := "text-content")(
        Markdown(
          remarkPlugins = Seq(RemarkPlugin.breaks),
          rehypePlugins =
            Seq(js.Tuple2(RehypePlugin.externalLinks, jso(target = "_blank")))
        )(content.content)
      )
    )

  def ulParents(
      parents: Seq[(Coto, Link)],
      dispatch: AppMsg => Unit
  ): Option[ReactElement] =
    Option.when(!parents.isEmpty) {
      ul(className := "parents")(
        parents.map { case (parent, link) =>
          li(key := link.id.uuid)(
            button(
              className := "parent default",
              onClick := (_ =>
                dispatch(
                  SectionTraversals.Msg.OpenTraversal(parent.id).toApp
                )
              )
            )(parent.abbreviate)
          )
        }
      )
    }

  def divLinksTraversal(
      coto: Coto,
      tipPlacement: String,
      dispatch: AppMsg => Unit
  ): Option[ReactElement] =
    Option.when(coto.outgoingLinks > 0) {
      div(className := "links")(
        toolButton(
          symbol = "view_headline",
          tip = "Links",
          tipPlacement = tipPlacement,
          classes = "open-traversal",
          onClick =
            () => dispatch(SectionTraversals.Msg.OpenTraversal(coto.id).toApp)
        )
      )
    }
}
