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
import cotoami.models.{Coto, CotoContent, Cotonoma, Id, Ito, WaitingPost}
import cotoami.repository.Nodes
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  Markdown
}

object PartsCoto {

  def article(
      coto: Coto,
      dispatch: Into[AppMsg] => Unit,
      classes: Seq[(String, Boolean)] = Seq.empty
  )(children: ReactElement*)(implicit context: Context): ReactElement =
    slinky.web.html.article(
      className := optionalClasses(
        Seq(
          ("coto", true),
          ("selected", context.repo.cotos.isSelecting(coto.id)),
          ("highlighted", context.isHighlighting(coto.id)),
          ("being-deleted", context.repo.beingDeleted(coto.id))
        ) ++ classes
      ),
      onMouseEnter := (_ => dispatch(AppMsg.Highlight(coto.id))),
      onMouseLeave := (_ => dispatch(AppMsg.Unhighlight)),
      onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
    )(children: _*)

  def addressAuthor(
      coto: Coto,
      nodes: Nodes
  ): ReactElement =
    address(className := "author")(
      nodes.get(coto.postedById).map(PartsNode.spanNode)
    )

  def divAttributes(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "attributes")(
      ulOtherCotonomas(coto),
      buttonDateTimeRange(coto),
      buttonGeolocation(coto),
      Option.when(context.repo.pinned(coto.id)) {
        div(className := "pinned", title := "Pinned")(
          materialSymbol("push_pin")
        )
      },
      Option.when(!context.repo.nodes.isOperating(coto.nodeId)) {
        val connected = context.repo.nodes.parentConnected(coto.nodeId)
        div(
          className := optionalClasses(
            Seq(
              ("remote-node-icon", true),
              ("connected", connected)
            )
          ),
          title := (if (connected) "Remote (connected)"
                    else "Remote (disconnected)")
        )(
          context.repo.nodes.get(coto.nodeId).map(PartsNode.imgNode(_))
        )
      }
    )

  private def ulOtherCotonomas(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    ul(className := "other-cotonomas")(
      coto.postedInIds
        .filter(id =>
          context.repo.cotonomas.focusedId match {
            case Some(focusedId) => id != focusedId
            case None            => !context.repo.nodes.isCurrentNodeRoot(id)
          }
        )
        .map(context.repo.cotonomas.get)
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
      context.repo.cotonomas.asCotonoma(coto)
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
        context.repo.nodes.get(cotonoma.nodeId).map(PartsNode.imgNode(_)),
        cotonoma.name
      )
    )

  def divContentPreview(
      coto: Coto,
      collapsibleContentOpened: Boolean = false
  )(implicit context: Context): ReactElement =
    div(className := "content")(
      context.repo.cotonomas.asCotonoma(coto).map(cotonoma =>
        section(className := "cotonoma-label")(
          span(className := "cotonoma")(
            context.repo.nodes.get(cotonoma.nodeId).map(PartsNode.imgNode(_)),
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
            context.repo.nodes.get(post.postedIn.nodeId)
              .map(PartsNode.imgNode(_)),
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

  def sectionCotonomaContent(cotoContent: CotoContent): Option[ReactElement] =
    Option.when(
      cotoContent.content.map(!_.isBlank()).getOrElse(false) ||
        cotoContent.mediaUrl.isDefined
    ) {
      section(className := "coto-content cotonoma-content")(
        cotoContent.mediaUrl.map(sectionMediaContent),
        sectionTextContent(cotoContent.content)
      )
    }

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

  def ulParents(
      parents: Seq[(Coto, Ito)],
      onClickTagger: Id[Coto] => Into[AppMsg]
  )(implicit dispatch: Into[AppMsg] => Unit): Option[ReactElement] =
    Option.when(!parents.isEmpty) {
      ul(className := "parents")(
        parents.map { case (parent, ito) =>
          li(key := ito.id.uuid)(
            button(
              className := "parent default",
              onClick := (_ => dispatch(onClickTagger(parent.id)))
            )(parent.abbreviate)
          )
        }
      )
    }

  def divItosTraversal(
      coto: Coto,
      tipPlacement: String
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(context.repo.itos.anyFrom(coto.id)) {
      div(className := "itos")(
        toolButton(
          symbol = "arrow_forward",
          tip = Some("Traverse"),
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
