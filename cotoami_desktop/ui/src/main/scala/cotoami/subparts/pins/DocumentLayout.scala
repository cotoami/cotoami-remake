package cotoami.subparts.pins

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.HTMLElement

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.core.facade.Hooks._
import slinky.web.html._

import marubinotto.components.ScrollArea

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Ito, Siblings}
import cotoami.subparts.{PartsIto, PartsNode, SectionPins}

@react object DocumentLayout {
  case class Props(
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
              tocRef.current.style.height = tocHeight(entry.contentRect.height)
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

    section(className := "document-layout", ref := rootRef)(
      div(className := "pins-with-toc")(
        sectionPinnedCotos(props.pins)(sectionSubCotos),
        divToc(props.pins, tocRef)
      )
    )
  }

  private def tocHeight(viewportHeight: Double): String =
    s"${viewportHeight - 16}px"

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
            className := "toc-entry"
          )(
            button(
              className := "default",
              onClick := (_ => dispatch(SectionPins.Msg.ScrollToPin(ito)))
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
}
