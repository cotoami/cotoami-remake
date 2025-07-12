package cotoami.subparts

import scala.util.chaining._

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.HTMLElement

import cats.effect.IO

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.core.facade.Hooks._
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.{toolButton, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Ito, Siblings, UiState}
import cotoami.repository.Root

object SectionPins {

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionPinsMsg(this)
  }

  object Msg {
    case class SwitchView(cotonoma: Id[Cotonoma], inColumns: Boolean)
        extends Msg
    case class ScrollToPin(pin: Ito) extends Msg
  }

  def update(msg: Msg)(implicit
      context: Context
  ): (Option[UiState], Cmd[AppMsg]) = {
    val default = (context.uiState, Cmd.none)
    msg match {
      case Msg.SwitchView(cotonoma, inColumns) =>
        context.uiState
          .map(_.setPinsInColumns(cotonoma, inColumns).pipe { state =>
            default.copy(_1 = Some(state), _2 = state.save)
          })
          .getOrElse(default)

      case Msg.ScrollToPin(pin) =>
        default.copy(
          _2 = Cmd(
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
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  final val PinsBodyId = "pins-body"

  def apply(
      uiState: UiState
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    (context.repo.pins, context.repo.currentCotonomaPair) match {
      case (Some(pins), Some(cotonoma)) =>
        Some(sectionPins(pins, uiState, cotonoma))
      case _ => None
    }
  }

  private def sectionPins(
      pins: Siblings,
      uiState: UiState,
      currentCotonoma: (Cotonoma, Coto)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val (cotonoma, cotonomaCoto) = currentCotonoma
    val inColumns = uiState.arePinsInColumns(cotonoma.id)
    section(className := "pins header-and-body fill")(
      header(cotonoma, cotonomaCoto, inColumns),
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
        ScrollArea(scrollableClassName = Some("scrollable-pins"))(
          if (inColumns)
            sectionPinnedCotos(pins, true)
          else
            DocumentView(
              cotonomaCoto = cotonomaCoto,
              pins = pins,
              viewportId = PinsBodyId,
              context = context,
              dispatch = dispatch
            )
        )
      )
    )
  }

  private def header(
      cotonoma: Cotonoma,
      cotonomaCoto: Coto,
      inColumns: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    slinky.web.html.header()(
      ToolbarCoto(cotonomaCoto),
      section(className := "title")(
        span(
          className := "current-cotonoma-name",
          onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(cotonomaCoto.id)))
        )(
          context.repo.nodes.get(cotonoma.nodeId).map(PartsNode.imgNode(_)),
          cotonoma.name
        )
      ),
      section(className := "view-switch")(
        toolButton(
          symbol = "view_agenda",
          tip = Some("Document"),
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
          tip = Some("Columns"),
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
    )

  @react object DocumentView {
    case class Props(
        cotonomaCoto: Coto,
        pins: Siblings,
        viewportId: String,
        context: Context,
        dispatch: Into[AppMsg] => Unit
    )

    final val ActiveTocEntryClass = "active"

    val component = FunctionalComponent[Props] { props =>
      implicit val _context: Context = props.context
      implicit val _dispatch = props.dispatch

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
          rootRef.current.querySelectorAll("section.pin")
            .foreach(intersectionObserver.observe)

          () => {
            resizeObserver.disconnect()
            intersectionObserver.disconnect()
          }
        },
        Seq(props.pins.fingerprint)
      )

      section(className := "document-view", ref := rootRef)(
        PartsCoto.sectionCotonomaContent(props.cotonomaCoto).map(
          div(
            className := "cotonoma-content",
            onDoubleClick := (_ =>
              props.dispatch(AppMsg.FocusCoto(props.cotonomaCoto.id))
            )
          )(_)
        ),
        div(className := "pins-with-toc")(
          sectionPinnedCotos(props.pins, false),
          divToc(props.pins, tocRef)
        )
      )
    }

    private def tocHeight(viewportHeight: Double): String =
      s"${viewportHeight - 16}px"
  }

  private def sectionPinnedCotos(
      pins: Siblings,
      inColumns: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsIto.sectionSiblings(pins, "pinned-cotos") { case (ito, coto, order) =>
      val subCotos = context.repo.childrenOf(coto.id)
      section(
        className := optionalClasses(
          Seq(
            ("pin", true),
            ("with-description", ito.description.isDefined)
          )
        ),
        id := elementIdOfPin(ito)
      )(
        PartsCoto.article(
          coto,
          dispatch,
          Seq(
            ("pinned-coto", true),
            ("has-children", context.repo.itos.anyFrom(coto.id))
          )
        )(
          PartsIto.buttonPin(ito),
          ToolbarCoto(coto),
          ToolbarReorder(ito, order),
          PartsCoto.ulParents(
            context.repo.parentsOf(coto.id).filter(_._2.id != ito.id),
            SectionTraversals.Msg.OpenTraversal(_).into
          ),
          div(className := "body")(
            PartsCoto.divContent(coto)
          ),
          footer()(
            PartsCoto.divAttributes(coto)
          )
        ),
        if (coto.isCotonoma && !context.repo.alreadyLoadedGraphFrom(coto.id)) {
          div(className := "itos-not-yet-loaded")(
            if (context.repo.graphLoading.contains(coto.id)) {
              div(
                className := "loading",
                aria - "busy" := "true"
              )()
            } else {
              toolButton(
                symbol = "more_horiz",
                tip = Some(context.i18n.text.LoadItos),
                tipPlacement = "bottom",
                classes = "fetch-itos",
                onClick = _ => dispatch(Root.Msg.FetchGraphFromCoto(coto.id))
              )
            }
          )
        } else {
          subCotos.map(sectionSubCotos(_, inColumns))
        }
      )
    }

  def elementIdOfPin(pin: Ito): String = s"pin-${pin.id.uuid}"

  private def elementIdOfTocEntry(pin: Ito): String =
    s"toc-${elementIdOfPin(pin)}"

  private def divToc(
      pins: Siblings,
      tocRef: ReactRef[dom.HTMLDivElement]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "toc", ref := tocRef)(
      ScrollArea()(
        PartsIto.sectionSiblings(pins) { case (ito, coto, order) =>
          div(
            id := elementIdOfTocEntry(ito),
            // This className will be modified directly by DocumentView to highlight
            // entries in the current viewport, so it must not be dynamic with the models.
            className := "toc-entry",
            onMouseEnter := (_ => dispatch(AppMsg.Highlight(coto.id))),
            onMouseLeave := (_ => dispatch(AppMsg.Unhighlight))
          )(
            button(
              className := optionalClasses(
                Seq(
                  ("default", true),
                  ("highlighted", context.isHighlighting(coto.id))
                )
              ),
              onClick := (_ => dispatch(Msg.ScrollToPin(ito)))
            )(
              if (coto.isCotonoma)
                span(className := "cotonoma")(
                  context.repo.nodes.get(coto.nodeId)
                    .map(PartsNode.imgNode(_)),
                  coto.nameAsCotonoma
                )
              else
                coto.abbreviate
            )
          )
        }
      )
    )

  private def sectionSubCotos(
      subCotos: Siblings,
      inColumn: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    if (inColumn) {
      ScrollArea(className = Some("scrollable-sub-cotos"))(
        sectionSubCotos(subCotos)
      )
    } else {
      sectionSubCotos(subCotos)
    }

  private def sectionSubCotos(
      subCotos: Siblings
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsIto.sectionSiblings(subCotos, "sub-cotos") { case (ito, coto, order) =>
      div(className := "sub-coto")(
        PartsCoto.article(coto, dispatch, Seq(("sub-coto", true)))(
          ToolbarCoto(coto),
          ToolbarReorder(ito, order),
          PartsIto.buttonSubcotoIto(ito),
          PartsCoto.ulParents(
            context.repo.parentsOf(coto.id).filter(_._2.id != ito.id),
            SectionTraversals.Msg.OpenTraversal(_).into
          ),
          div(className := "body")(
            PartsCoto.divContent(coto),
            divItosTraversalButton(coto)
          ),
          footer()(
            PartsCoto.divAttributes(coto)
          )
        )
      )
    }

  def divItosTraversalButton(
      coto: Coto
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(context.repo.itos.anyFrom(coto.id)) {
      div(className := "has-itos")(
        toolButton(
          symbol = "arrow_forward",
          classes = "open-traversal",
          onClick = e => {
            e.stopPropagation()
            dispatch(SectionTraversals.Msg.OpenTraversal(coto.id))
          }
        )
      )
    }
}
