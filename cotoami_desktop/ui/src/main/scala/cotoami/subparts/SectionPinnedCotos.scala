package cotoami.subparts

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.HTMLElement

import cats.effect.IO

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.core.facade.Hooks._
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Link, UiState}
import cotoami.repositories.Domain
import cotoami.components.{optionalClasses, toolButton, ScrollArea}

object SectionPinnedCotos {

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionPinnedCotosMsg(this)
  }

  object Msg {
    case class SwitchView(cotonoma: Id[Cotonoma], inColumns: Boolean)
        extends Msg
    case class ScrollToPinnedCoto(pin: Link) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.SwitchView(cotonoma, inColumns) =>
        model.uiState
          .map(_.setPinnedInColumns(cotonoma, inColumns) match {
            case state => (model.copy(uiState = Some(state)), state.save)
          })
          .getOrElse((model, Cmd.none))

      case Msg.ScrollToPinnedCoto(pin) =>
        (
          model,
          Cmd(
            IO {
              dom.document.getElementById(elementIdOfPinnedCoto(pin)) match {
                case element: HTMLElement =>
                  element.scrollIntoView(true)
                case _ => ()
              }
              None
            }
          )
        )
    }

  final val PinnedCotosBodyId = "pinned-cotos-body"

  def apply(
      model: Model,
      uiState: UiState
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    (context.domain.pinnedCotos, model.domain.currentCotonoma) match {
      case (pinnedCotos, Some(cotonoma)) if !pinnedCotos.isEmpty =>
        Some(sectionPinnedCotos(pinnedCotos, uiState, cotonoma))
      case _ => None
    }
  }

  def sectionPinnedCotos(
      pinnedCotos: Seq[(Link, Coto)],
      uiState: UiState,
      currentCotonoma: Cotonoma
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val inColumns = uiState.isPinnedInColumns(currentCotonoma.id)

    section(className := "pinned-cotos header-and-body")(
      header(className := "tools")(
        toolButton(
          symbol = "view_column",
          tip = "Columns",
          classes = optionalClasses(
            Seq(
              ("view-columns", true),
              ("selected", inColumns)
            )
          ),
          disabled = inColumns,
          onClick = _ => dispatch(Msg.SwitchView(currentCotonoma.id, true))
        ),
        toolButton(
          symbol = "view_agenda",
          tip = "Document",
          classes = optionalClasses(
            Seq(
              ("view-document", true),
              ("selected", !inColumns)
            )
          ),
          disabled = !inColumns,
          onClick = _ => dispatch(Msg.SwitchView(currentCotonoma.id, false))
        )
      ),
      div(
        className := optionalClasses(
          Seq(
            ("body", true),
            ("document-view", !inColumns),
            ("column-view", inColumns)
          )
        ),
        id := PinnedCotosBodyId
      )(
        ScrollArea()(
          if (inColumns)
            olPinnedCotos(pinnedCotos, inColumns)
          else
            DocumentView(
              pinnedCotos = pinnedCotos,
              viewportId = PinnedCotosBodyId,
              context = context,
              dispatch = dispatch
            )
        )
      )
    )
  }

  @react object DocumentView {
    case class Props(
        pinnedCotos: Seq[(Link, Coto)],
        viewportId: String,
        context: Context,
        dispatch: Into[AppMsg] => Unit
    )

    final val ActiveTocEntryClass = "active"

    val component = FunctionalComponent[Props] { props =>
      val rootRef = useRef[html.Div](null)
      val tocRef = useRef[html.Div](null)

      useEffect(
        () => {
          // Viewport element
          val viewport =
            dom.document.getElementById(props.viewportId) match {
              case element: HTMLElement => element
              case _ =>
                throw new IllegalArgumentException(
                  s"Invalid viewportId: ${props.viewportId}"
                )
            }

          // Initialize the TOC height
          tocRef.current.style.height = tocHeight(viewport.offsetHeight)

          // Resize the TOC height according to the viewport size
          val resizeObserver = new dom.ResizeObserver((entries, observer) => {
            entries.foreach(entry => {
              if (tocRef.current != null) {
                tocRef.current.style.height =
                  tocHeight(entry.contentRect.height)
              }
            })
          })
          resizeObserver.observe(viewport)

          // Highlight TOC entries according to the current viewport position
          val intersectionObserver = new dom.IntersectionObserver(
            (entries, observer) =>
              entries.foreach(entry => {
                val id = entry.target.getAttribute("id")
                // Directly modify the class of the corresponding TOC entry element
                // for performance reasons.
                dom.document.getElementById(s"toc-${id}") match {
                  case element: HTMLElement => {
                    if (entry.intersectionRatio > 0)
                      element.classList.add(ActiveTocEntryClass)
                    else
                      element.classList.remove(ActiveTocEntryClass)
                  }
                  case _ => ()
                }
              }),
            new dom.IntersectionObserverInit {
              root = viewport
            }
          )
          rootRef.current.querySelectorAll("li.pin").foreach(
            intersectionObserver.observe(_)
          )

          () => {
            resizeObserver.disconnect()
            intersectionObserver.disconnect()
          }
        },
        props.pinnedCotos
      )

      div(className := "pinned-cotos-with-toc", ref := rootRef)(
        olPinnedCotos(props.pinnedCotos, false)(props.context, props.dispatch),
        divToc(props.pinnedCotos, tocRef)(props.context, props.dispatch)
      )
    }

    private def tocHeight(viewportHeight: Double): String =
      s"${viewportHeight - 16}px"
  }

  private def olPinnedCotos(
      pinnedCotos: Seq[(Link, Coto)],
      inColumns: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    ol(className := "pinned-cotos")(
      pinnedCotos.map { case (pin, coto) =>
        liPinnedCoto(pin, coto, inColumns)
      }: _*
    )

  def elementIdOfPinnedCoto(pin: Link): String = s"pin-${pin.id.uuid}"

  private def liPinnedCoto(
      pin: Link,
      coto: Coto,
      inColumn: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    li(
      key := pin.id.uuid,
      className := "pin",
      id := elementIdOfPinnedCoto(pin)
    )(
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id).filter(_._2.id != pin.id),
        SectionTraversals.Msg.OpenTraversal(_).into
      ),
      article(
        className := optionalClasses(
          ViewCoto.commonArticleClasses(coto) ++
            Seq(
              ("pinned-coto", true),
              ("has-children", context.domain.links.anyFrom(coto.id))
            )
        ),
        onClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
      )(
        ToolbarCoto(coto),
        div(className := "body")(
          toolButton(
            symbol = "push_pin",
            tip = "Unpin",
            tipPlacement = "right",
            classes = "unpin"
          ),
          ViewCoto.divContent(coto)
        ),
        footer()(
          ViewCoto.divAttributes(coto)
        )
      ),
      olSubCotos(coto, inColumn)
    )
  }

  private def elementIdOfTocEntry(pin: Link): String =
    s"toc-${elementIdOfPinnedCoto(pin)}"

  private def divToc(
      pinnedCotos: Seq[(Link, Coto)],
      tocRef: ReactRef[dom.HTMLDivElement]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "toc", ref := tocRef)(
      ScrollArea()(
        ol(className := "toc")(
          pinnedCotos.map { case (pin, coto) =>
            li(
              key := pin.id.uuid,
              className := "toc-entry",
              id := elementIdOfTocEntry(pin)
            )(
              button(
                className := "default",
                onClick := (_ => dispatch(Msg.ScrollToPinnedCoto(pin)))
              )(
                if (coto.isCotonoma)
                  span(className := "cotonoma")(
                    context.domain.nodes.get(coto.nodeId).map(imgNode(_)),
                    coto.nameAsCotonoma
                  )
                else
                  coto.abbreviate
              )
            )
          }: _*
        )
      )
    )

  private def olSubCotos(
      coto: Coto,
      inColumn: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val subCotos = context.domain.childrenOf(coto.id)
    ol(className := "sub-cotos")(
      if (coto.isCotonoma && !context.domain.alreadyLoadedGraphFrom(coto.id))
        div(className := "links-not-yet-loaded")(
          if (context.domain.graphLoading.contains(coto.id)) {
            div(
              className := "loading",
              aria - "busy" := "true"
            )()
          } else {
            toolButton(
              symbol = "more_horiz",
              tip = "Load links",
              tipPlacement = "bottom",
              classes = "fetch-links",
              onClick = _ => dispatch(Domain.Msg.FetchGraphFromCoto(coto.id))
            )
          }
        )
      else
        subCotos.map { case (link, subCoto) =>
          liSubCoto(link, subCoto)
        }
    ) match {
      case olSubCotos =>
        if (inColumn) {
          div(className := "scrollable-sub-cotos")(
            ScrollArea()(olSubCotos)
          )
        } else {
          olSubCotos
        }
    }
  }

  private def liSubCoto(
      link: Link,
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    li(key := link.id.uuid, className := "sub")(
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id).filter(_._2.id != link.id),
        SectionTraversals.Msg.OpenTraversal(_).into
      ),
      article(
        className := optionalClasses(
          ViewCoto.commonArticleClasses(coto) ++
            Seq(("sub-coto", true))
        ),
        onClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
      )(
        ToolbarCoto(coto),
        header()(
          toolButton(
            symbol = "subdirectory_arrow_right",
            tip = "Unlink",
            tipPlacement = "right",
            classes = "unlink"
          )
        ),
        div(className := "body")(
          ViewCoto.divContent(coto),
          ViewCoto.divLinksTraversal(coto, "left")
        ),
        footer()(
          ViewCoto.divAttributes(coto)
        )
      )
    )
}
