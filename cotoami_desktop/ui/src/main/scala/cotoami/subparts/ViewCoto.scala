package cotoami.subparts

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.libs.{rehypePlugins, remarkPlugins}
import cotoami.models.{Coto, CotoContent, Cotonoma, Id, Link, WaitingPost}
import cotoami.repositories.Nodes
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  Markdown
}

object ViewCoto {

  def commonArticleClasses(
      coto: Coto
  )(implicit context: Context): Seq[(String, Boolean)] =
    Seq(
      ("coto", true),
      ("being-deleted", context.domain.beingDeleted(coto.id))
    )

  def addressAuthor(
      coto: Coto,
      nodes: Nodes
  ): ReactElement =
    address(className := "author")(
      nodes.get(coto.postedById).map(spanNode)
    )

  def divAttributes(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "attributes")(
      ulOtherCotonomas(coto),
      buttonDateTimeRange(coto),
      buttonGeolocation(coto),
      Option.when(context.domain.pinned(coto.id)) {
        div(className := "pinned", title := "Pinned")(
          materialSymbol("push_pin")
        )
      },
      Option.when(!context.domain.nodes.isOperating(coto.nodeId)) {
        if (context.domain.nodes.asChildOf(coto.nodeId).isDefined)
          div(
            className := "connection connected",
            title := "Remote (connected)"
          )(materialSymbol("link"))
        else
          div(
            className := "connection disconnected",
            title := "Remote (disconnected)"
          )(
            materialSymbol("link_off")
          )
      }
    )

  private def ulOtherCotonomas(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    ul(className := "other-cotonomas")(
      coto.postedInIds
        .filter(id =>
          context.domain.cotonomas.focusedId match {
            case Some(focusedId) => id != focusedId
            case None            => !context.domain.nodes.isCurrentNodeRoot(id)
          }
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
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "content")(
      context.domain.cotonomas.asCotonoma(coto)
        .map(sectionCotonomaLabel)
        .getOrElse(sectionCotoContent(coto, collapsibleContentOpened))
    )

  def sectionCotonomaLabel(
      cotonoma: Cotonoma
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "cotonoma-label")(
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

  def divContentPreview(
      coto: Coto,
      collapsibleContentOpened: Boolean = false
  )(implicit context: Context): ReactElement =
    div(className := "content")(
      context.domain.cotonomas.asCotonoma(coto).map(cotonoma =>
        section(className := "cotonoma-content")(
          span(className := "cotonoma")(
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
      cotoContent.mediaUrl.map(sectionMediaContent),
      cotoContent.summary.map(summary => {
        CollapsibleContent(
          summary = summary,
          details = sectionTextContent(cotoContent.content),
          opened = collapsibleContentOpened
        ): ReactElement
      }).getOrElse(sectionTextContent(cotoContent.content))
    )

  def sectionCotonomaContent(cotoContent: CotoContent): ReactElement =
    section(className := "coto-content cotonoma-content")(
      cotoContent.mediaUrl.map(sectionMediaContent),
      sectionTextContent(cotoContent.content)
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

  def sectionMediaContent(urlAndType: (String, String)): ReactElement = {
    val (url, mediaType) = urlAndType
    section(className := "media-content")(
      if (mediaType.startsWith("image/")) {
        Some(
          img(
            className := "media-image",
            alt := "Image content",
            src := url
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
        nodeRoot.mediaUrl.isDefined
    ) {
      section(className := "node-description")(
        nodeRoot.mediaUrl.map(sectionMediaContent),
        sectionTextContent(nodeRoot.content)
      )
    }

  def ulParents(
      parents: Seq[(Coto, Link)],
      onClickTagger: Id[Coto] => Into[AppMsg]
  )(implicit dispatch: Into[AppMsg] => Unit): Option[ReactElement] =
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
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(context.domain.links.anyFrom(coto.id)) {
      div(className := "links")(
        toolButton(
          symbol = "arrow_forward",
          tip = "Traverse",
          tipPlacement = tipPlacement,
          classes = "open-traversal",
          onClick = e => {
            e.stopPropagation()
            dispatch(SectionTraversals.Msg.OpenTraversal(coto.id))
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
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    coto.geolocation.map { location =>
      val focused = context.geomap.isFocusing(location)
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

  def buttonDateTimeRange(
      coto: Coto
  )(implicit
      context: Context
  ): Option[ReactElement] =
    coto.dateTimeRange.map(range =>
      button(
        className := "time-range default",
        data - "tooltip" := context.time.formatDateTime(range.start),
        data - "placement" := "left",
        onClick := (e => {
          e.stopPropagation()
        })
      )(
        materialSymbol("calendar_month")
      )
    )
}
