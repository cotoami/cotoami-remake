package cotoami.subparts

import scala.util.chaining._
import scala.collection.immutable.HashSet

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.HTMLElement

import cats.effect.IO
import com.softwaremill.quicklens._

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.core.facade.Hooks._
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Link, UiState}
import cotoami.repositories.Domain
import cotoami.components.{optionalClasses, toolButton, ScrollArea}

object SectionPins {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      justPinned: HashSet[Id[Coto]] = HashSet.empty
  ) {
    def removeFromJustPinned(cotoId: Id[Coto]): Model =
      this.modify(_.justPinned).using(_ - cotoId)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionPinsMsg(this)
  }

  object Msg {
    case class SwitchView(cotonoma: Id[Cotonoma], inColumns: Boolean)
        extends Msg
    case class ScrollToPin(pin: Link) extends Msg
    case class PinAnimationEnd(cotoId: Id[Coto]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Option[UiState], Cmd[AppMsg]) = {
    val default = (model, context.uiState, Cmd.none)
    msg match {
      case Msg.SwitchView(cotonoma, inColumns) =>
        context.uiState
          .map(_.setPinsInColumns(cotonoma, inColumns).pipe { state =>
            default.copy(_2 = Some(state), _3 = state.save)
          })
          .getOrElse(default)

      case Msg.ScrollToPin(pin) =>
        default.copy(
          _3 = Cmd(
            IO {
              dom.document.getElementById(elementIdOfPin(pin)) match {
                case element: HTMLElement =>
                  element.scrollIntoView(true)
                case _ => ()
              }
              None
            }
          )
        )

      case Msg.PinAnimationEnd(cotoId) =>
        default.copy(_1 = model.removeFromJustPinned(cotoId))
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  final val PinsBodyId = "pins-body"

  def apply(
      model: Model,
      uiState: UiState
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    context.domain.currentCotonomaPair.map(
      sectionPins(context.domain.pins, model, uiState, _)
    )
  }

  def sectionPins(
      pins: Seq[(Link, Coto)],
      model: Model,
      uiState: UiState,
      currentCotonoma: (Cotonoma, Coto)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val (cotonoma, cotonomaCoto) = currentCotonoma
    val inColumns = uiState.arePinsInColumns(cotonoma.id)
    section(className := "pins header-and-body")(
      header()(
        ToolbarCoto(cotonomaCoto),
        section(className := "title")(
          span(
            className := "current-cotonoma-name",
            onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(cotonomaCoto.id)))
          )(
            context.domain.nodes.get(cotonoma.nodeId).map(imgNode(_)),
            cotonoma.name
          )
        ),
        section(className := "view-switch")(
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
            onClick = _ => dispatch(Msg.SwitchView(cotonoma.id, false))
          ),
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
            onClick = _ => dispatch(Msg.SwitchView(cotonoma.id, true))
          )
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
        id := PinsBodyId
      )(
        ScrollArea()(
          if (inColumns)
            olPins(pins, inColumns, model.justPinned)
          else
            DocumentView(
              cotonomaCoto = cotonomaCoto,
              pins = pins,
              viewportId = PinsBodyId,
              justPinned = model.justPinned,
              context = context,
              dispatch = dispatch
            )
        )
      )
    )
  }

  @react object DocumentView {
    case class Props(
        cotonomaCoto: Coto,
        pins: Seq[(Link, Coto)],
        viewportId: String,
        justPinned: HashSet[Id[Coto]],
        context: Context,
        dispatch: Into[AppMsg] => Unit
    ) {
      val version: String = pins.map(_._1.id.uuid).mkString
    }

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
          rootRef.current.querySelectorAll("li.pin")
            .foreach(intersectionObserver.observe)

          () => {
            resizeObserver.disconnect()
            intersectionObserver.disconnect()
          }
        },
        Seq(props.version)
      )

      section(className := "document-view", ref := rootRef)(
        ViewCoto.sectionCotonomaContent(props.cotonomaCoto).map(
          div(
            className := "cotonoma-content",
            onDoubleClick := (_ =>
              props.dispatch(AppMsg.FocusCoto(props.cotonomaCoto.id))
            )
          )(_)
        ),
        div(className := "pins-with-toc")(
          olPins(props.pins, false, props.justPinned)(
            props.context,
            props.dispatch
          ),
          divToc(props.pins, tocRef)(props.context, props.dispatch)
        )
      )
    }

    private def tocHeight(viewportHeight: Double): String =
      s"${viewportHeight - 16}px"
  }

  private def olPins(
      pins: Seq[(Link, Coto)],
      inColumns: Boolean,
      justPinned: HashSet[Id[Coto]]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    ol(className := "pins")(
      pins.map { case (pin, coto) =>
        liPin(pin, coto, inColumns, justPinned.contains(coto.id))
      }: _*
    )

  def elementIdOfPin(pin: Link): String = s"pin-${pin.id.uuid}"

  private def liPin(
      pin: Link,
      coto: Coto,
      inColumn: Boolean,
      justPinned: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    li(
      key := pin.id.uuid,
      className := optionalClasses(
        Seq(
          ("pin", true),
          ("just-pinned", justPinned)
        )
      ),
      id := elementIdOfPin(pin),
      onAnimationEnd := (e => {
        if (e.animationName == "just-pinned") {
          dispatch(Msg.PinAnimationEnd(coto.id).into)
        }
      })
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
        onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
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
    s"toc-${elementIdOfPin(pin)}"

  private def divToc(
      pins: Seq[(Link, Coto)],
      tocRef: ReactRef[dom.HTMLDivElement]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "toc", ref := tocRef)(
      ScrollArea()(
        ol(className := "toc")(
          pins.map { case (pin, coto) =>
            li(
              key := pin.id.uuid,
              className := "toc-entry",
              id := elementIdOfTocEntry(pin)
            )(
              button(
                className := "default",
                onClick := (_ => dispatch(Msg.ScrollToPin(pin)))
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
        onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
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
