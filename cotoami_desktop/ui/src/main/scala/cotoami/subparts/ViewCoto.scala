package cotoami.subparts

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.{Context, Msg => AppMsg}
import cotoami.libs.{rehypePlugins, remarkPlugins}
import cotoami.models.{Coto, CotoContent, Id, WaitingPost}
import cotoami.repositories.Nodes
import cotoami.backend.Link
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  Markdown
}

object ViewCoto {

  def addressAuthor(
      coto: Coto,
      nodes: Nodes
  ): ReactElement =
    address(className := "author")(
      nodes.get(coto.postedById).map(spanNode)
    )

  def divAttributes(
      coto: Coto
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    div(className := "attributes")(
      ulOtherCotonomas(coto),
      buttonGeolocation(coto),
      Option.when(context.domain.pinned(coto.id)) {
        div(className := "pinned")(materialSymbol("push_pin"))
      }
    )

  private def ulOtherCotonomas(
      coto: Coto
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    ul(className := "other-cotonomas")(
      coto.postedInIds
        .filter(id =>
          !context.domain.isCurrentRoot(id) &&
            !context.domain.cotonomas.isFocusing(id)
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
                e.stopPropagation()
                dispatch(AppMsg.FocusCotonoma(cotonoma))
              })
            )(cotonoma.name)
          )
        ): _*
    )

  def divContent(
      coto: Coto,
      collapsibleContentOpened: Boolean = false
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    div(className := "content")(
      context.domain.cotonomas.asCotonoma(coto).map(cotonoma =>
        section(className := "cotonoma-content")(
          a(
            className := "cotonoma",
            title := cotonoma.name,
            onClick := ((e) => {
              e.preventDefault()
              dispatch(AppMsg.FocusCotonoma(cotonoma))
            })
          )(
            context.domain.nodes.get(cotonoma.nodeId).map(imgNode(_)),
            cotonoma.name
          )
        )
      ).getOrElse(sectionCotoContent(coto, collapsibleContentOpened))
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
      collapsibleContentOpened: Boolean
  ): ReactElement =
    section(className := "coto-content")(
      cotoContent.mediaContent.map(sectionMediaContent),
      cotoContent.summary.map(summary => {
        CollapsibleContent(
          summary = summary,
          details = sectionTextContent(cotoContent.content),
          opened = collapsibleContentOpened
        ): ReactElement
      }).getOrElse(sectionTextContent(cotoContent.content))
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
            onClick := (e => {
              e.stopPropagation()
              setOpened(!opened)
            })
          )(
            if (opened)
              materialSymbol("keyboard_double_arrow_up")
            else
              materialSymbol("keyboard_double_arrow_down")
          ),
          span(
            className := "summary",
            onClick := (e => {
              e.stopPropagation()
              setOpened(!opened)
            })
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

  def sectionTextContent(content: Option[String]): ReactElement =
    section(className := "text-content")(
      Markdown(
        remarkPlugins = Seq(remarkPlugins.breaks),
        rehypePlugins =
          Seq(js.Tuple2(rehypePlugins.externalLinks, jso(target = "_blank")))
      )(content)
    )

  def sectionMediaContent(content: (String, String)): ReactElement = {
    val (mediaContent, mediaType) = content
    section(className := "media-content")(
      if (mediaType.startsWith("image/")) {
        Some(
          img(
            className := "media-image",
            alt := "Image content",
            src := s"data:${mediaType};base64,${mediaContent}"
          )
        )
      } else {
        None
      }
    )
  }

  def sectionNodeDescription(nodeRoot: CotoContent): Option[ReactElement] =
    Option.when(
      nodeRoot.content.map(!_.isBlank()).getOrElse(false) ||
        nodeRoot.mediaContent.isDefined
    ) {
      section(className := "node-description")(
        nodeRoot.mediaContent.map(sectionMediaContent),
        sectionTextContent(nodeRoot.content)
      )
    }

  def ulParents(
      parents: Seq[(Coto, Link)],
      onClickTagger: Id[Coto] => AppMsg
  )(implicit dispatch: AppMsg => Unit): Option[ReactElement] =
    Option.when(!parents.isEmpty) {
      ul(className := "parents")(
        parents.map { case (parent, link) =>
          li(key := link.id.uuid)(
            button(
              className := "parent default",
              onClick := (_ => dispatch(onClickTagger(parent.id)))
            )(parent.abbreviate)
          )
        }
      )
    }

  def divLinksTraversal(
      coto: Coto,
      tipPlacement: String
  )(implicit dispatch: AppMsg => Unit): Option[ReactElement] =
    Option.when(coto.outgoingLinks > 0) {
      div(className := "links")(
        toolButton(
          symbol = "arrow_forward",
          tip = "Traverse",
          tipPlacement = tipPlacement,
          classes = "open-traversal",
          onClick = e => {
            e.stopPropagation()
            dispatch(SectionTraversals.Msg.OpenTraversal(coto.id).toApp)
          }
        )
      )
    }

  def articleFooter(
      coto: Coto
  )(implicit context: Context): ReactElement =
    footer()(
      time(
        className := "posted-at",
        title := context.time.formatDateTime(coto.createdAt),
        onClick := (e => {
          e.stopPropagation()
        })
      )(
        context.time.display(coto.createdAt)
      )
    )

  def buttonGeolocation(
      coto: Coto
  )(implicit context: Context, dispatch: AppMsg => Unit): Option[ReactElement] =
    coto.geolocation.map { location =>
      val focused = Some(location) == context.focusedLocation
      button(
        className := optionalClasses(
          Seq(
            ("geolocation", true),
            ("default", true),
            ("focused", focused)
          )
        ),
        onClick := (e => {
          e.stopPropagation()
          dispatch(
            if (focused)
              AppMsg.UnfocusGeolocation
            else
              AppMsg.FocusGeolocation(location)
          )
        })
      )(
        materialSymbol("location_on")
      )
    }
}
