package cotoami.subparts

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.HTMLElement

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{Fragment, React, ReactElement}
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.{Context, Model, Msg => AppMsg}
import cotoami.backend.{Coto, Cotonoma, Link}
import cotoami.models.UiState
import cotoami.repositories.Domain
import cotoami.components.{optionalClasses, toolButton, MapLibre, ScrollArea}

object PaneStock {
  final val PaneName = "PaneStock"
  final val ScrollableElementId = "scrollable-stock-with-traversals"
  final val PinnedCotosBodyId = "pinned-cotos-body"

  def apply(
      model: Model,
      uiState: UiState
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
    section(
      className := optionalClasses(
        Seq(
          ("stock", true)
        )
      )
    )(
      sectionLinkedCotos(model, uiState)(model, dispatch)
    )
  }

  def sectionLinkedCotos(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val pinnedCotos = context.domain.pinnedCotos
    val sectionTraversals = SectionTraversals(model.traversals)
    val contents = Fragment(
      (pinnedCotos.isEmpty, model.domain.currentCotonoma) match {
        case (false, Some(cotonoma)) =>
          Some(sectionPinnedCotos(pinnedCotos, uiState, cotonoma))
        case _ => None
      },
      sectionTraversals
    )

    section(
      className := optionalClasses(
        Seq(
          ("linked-cotos", true),
          ("with-traversals-opened", sectionTraversals.isDefined)
        )
      )
    )(
      if (sectionTraversals.isDefined)
        ScrollArea(scrollableElementId = Some(ScrollableElementId))(contents)
      else
        contents
    )
  }

  def sectionPinnedCotos(
      pinned: Seq[(Link, Coto)],
      uiState: UiState,
      currentCotonoma: Cotonoma
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
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
          onClick =
            _ => dispatch(AppMsg.SwitchPinnedView(currentCotonoma.id, true))
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
          onClick =
            _ => dispatch(AppMsg.SwitchPinnedView(currentCotonoma.id, false))
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
            olPinnedCotos(pinned, inColumns)
          else
            DocumentView(
              pinned = pinned,
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
        pinned: Seq[(Link, Coto)],
        viewportId: String,
        context: Context,
        dispatch: AppMsg => Unit
    )

    final val ActiveTocEntryClass = "active"

    val component = FunctionalComponent[Props] { props =>
      val rootRef = React.createRef[html.Div]

      useEffect(
        () => {
          val options = new dom.IntersectionObserverInit {
            root = dom.document.getElementById(props.viewportId) match {
              case element: HTMLElement => element
              case _                    => ()
            }
          }
          val observer = new dom.IntersectionObserver(
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
            options
          )
          rootRef.current.querySelectorAll("li.pin").foreach(
            observer.observe(_)
          )

          () => {
            observer.disconnect()
          }
        },
        props.pinned
      )

      div(className := "pinned-cotos-with-toc", ref := rootRef)(
        olPinnedCotos(props.pinned, false)(props.context, props.dispatch),
        divToc(props.pinned)(props.context, props.dispatch)
      )
    }
  }

  private def olPinnedCotos(
      pinned: Seq[(Link, Coto)],
      inColumns: Boolean
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    ol(className := "pinned-cotos")(
      pinned.map { case (pin, coto) =>
        liPinnedCoto(pin, coto, inColumns)
      }: _*
    )

  def elementIdOfPinnedCoto(pin: Link): String = s"pin-${pin.id.uuid}"

  private def liPinnedCoto(
      pin: Link,
      coto: Coto,
      inColumn: Boolean
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    li(
      key := pin.id.uuid,
      className := "pin",
      id := elementIdOfPinnedCoto(pin)
    )(
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id).filter(_._2.id != pin.id)
      ),
      article(
        className := optionalClasses(
          Seq(
            ("pinned-coto", true),
            ("coto", true),
            ("has-children", coto.outgoingLinks > 0)
          )
        ),
        onClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
      )(
        header()(
          ViewCoto.divClassifiedAs(coto)
        ),
        div(className := "body")(
          toolButton(
            symbol = "push_pin",
            tip = "Unpin",
            tipPlacement = "right",
            classes = "unpin"
          ),
          ViewCoto.divContent(coto)
        )
      ),
      olSubCotos(coto, inColumn)
    )
  }

  private def elementIdOfTocEntry(pin: Link): String =
    s"toc-${elementIdOfPinnedCoto(pin)}"

  private def divToc(
      pinned: Seq[(Link, Coto)]
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    div(className := "toc")(
      ScrollArea()(
        ol(className := "toc")(
          pinned.map { case (pin, coto) =>
            li(
              key := pin.id.uuid,
              className := "toc-entry",
              id := elementIdOfTocEntry(pin)
            )(
              button(
                className := "default",
                onClick := (_ => dispatch(AppMsg.ScrollToPinnedCoto(pin)))
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
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val subCotos = context.domain.childrenOf(coto.id)
    ol(className := "sub-cotos")(
      if (subCotos.size < coto.outgoingLinks)
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
              onClick =
                _ => dispatch(Domain.Msg.FetchGraphFromCoto(coto.id).toApp)
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
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    li(key := link.id.uuid, className := "sub")(
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id).filter(_._2.id != link.id)
      ),
      article(
        className := "sub-coto coto",
        onClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
      )(
        header()(
          toolButton(
            symbol = "subdirectory_arrow_right",
            tip = "Unlink",
            tipPlacement = "right",
            classes = "unlink"
          ),
          ViewCoto.divClassifiedAs(coto)
        ),
        div(className := "body")(
          ViewCoto.divContent(coto),
          ViewCoto.divLinksTraversal(coto, "left")
        )
      )
    )
}
