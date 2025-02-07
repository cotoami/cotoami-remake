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

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{
  Coto,
  Cotonoma,
  Id,
  Link,
  OrderContext,
  Siblings,
  UiState
}
import cotoami.repositories.Domain
import cotoami.components.{
  optionalClasses,
  toolButton,
  Flipped,
  Flipper,
  ScrollArea
}

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
    case class ScrollToPin(pin: Link) extends Msg
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
    context.domain.currentCotonomaPair.map(
      sectionPins(context.domain.pins, uiState, _)
    )
  }

  def sectionPins(
      pins: Siblings,
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
            olPins(pins, inColumns)
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
        Seq(props.pins.fingerprint)
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
          olPins(props.pins, false)(
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
      pins: Siblings,
      inColumns: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Flipper(element = "ol", className = "pins", flipKey = pins.fingerprint)(
      pins.eachWithOrderContext.map(pin =>
        Flipped(key = pin._1.id.uuid, flipId = pin._1.id.uuid)(
          liPin(pin, inColumns)
        ): ReactElement
      ).toSeq: _*
    )

  def elementIdOfPin(pin: Link): String = s"pin-${pin.id.uuid}"

  private def liPin(
      pin: (Link, Coto, OrderContext),
      inColumn: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val (link, coto, order) = pin
    val canEditPin = context.domain.nodes.canEdit(link)
    val subCotos = context.domain.childrenOf(coto.id)
    li(
      className := optionalClasses(
        Seq(
          ("pin", true),
          ("with-linking-phrase", link.linkingPhrase.isDefined)
        )
      ),
      id := elementIdOfPin(link)
    )(
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id).filter(_._2.id != link.id),
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
        div(
          className := optionalClasses(
            Seq(
              ("link-container", true),
              ("with-linking-phrase", link.linkingPhrase.isDefined)
            )
          )
        )(
          div(
            className := optionalClasses(
              Seq(
                ("pin", true),
                ("link", true),
                ("editable", canEditPin)
              )
            )
          )(
            toolButton(
              classes = "edit-pin",
              symbol = "push_pin",
              tip = Option.when(canEditPin)("Edit pin"),
              tipPlacement = "right",
              disabled = !canEditPin,
              onClick = e => {
                e.stopPropagation()
                dispatch(Modal.Msg.OpenModal(Modal.LinkEditor(link)))
              }
            ),
            link.linkingPhrase.map(phrase =>
              section(
                className := "linking-phrase",
                onClick := (e => {
                  e.stopPropagation()
                  if (canEditPin)
                    dispatch(Modal.Msg.OpenModal(Modal.LinkEditor(link)))
                })
              )(phrase)
            )
          )
        ),
        ToolbarCoto(coto),
        ToolbarReorder(link, order),
        div(className := "body")(
          ViewCoto.divContent(coto)
        ),
        footer()(
          ViewCoto.divAttributes(coto)
        )
      ),
      Option.when(!subCotos.isEmpty) {
        olSubCotos(coto, subCotos, inColumn)
      }
    )
  }

  private def elementIdOfTocEntry(pin: Link): String =
    s"toc-${elementIdOfPin(pin)}"

  private def divToc(
      pins: Siblings,
      tocRef: ReactRef[dom.HTMLDivElement]
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "toc", ref := tocRef)(
      ScrollArea()(
        Flipper(element = "ol", className = "toc", flipKey = pins.fingerprint)(
          pins.eachWithOrderContext.map { case (link, coto, order) =>
            Flipped(key = link.id.uuid, flipId = link.id.uuid)(
              li(
                className := "toc-entry",
                id := elementIdOfTocEntry(link)
              )(
                button(
                  className := "default",
                  onClick := (_ => dispatch(Msg.ScrollToPin(link)))
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
            ): ReactElement
          }.toSeq: _*
        )
      )
    )

  private def olSubCotos(
      coto: Coto,
      subCotos: Siblings,
      inColumn: Boolean
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    Flipper(
      element = "ol",
      className = "sub-cotos",
      flipKey = subCotos.fingerprint
    )(
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
              tip = Some("Load links"),
              tipPlacement = "bottom",
              classes = "fetch-links",
              onClick = _ => dispatch(Domain.Msg.FetchGraphFromCoto(coto.id))
            )
          }
        )
      else
        subCotos.eachWithOrderContext.map { case (link, subCoto, order) =>
          Flipped(key = link.id.uuid, flipId = link.id.uuid)(
            liSubCoto(link, subCoto, order)
          )
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
      coto: Coto,
      order: OrderContext
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    li(className := "sub")(
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
        ToolbarReorder(link, order),
        header()(
          subcotoLink(link)
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
