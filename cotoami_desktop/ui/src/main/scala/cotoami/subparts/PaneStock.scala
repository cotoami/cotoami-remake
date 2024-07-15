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
import cotoami.components.{optionalClasses, ScrollArea, ToolButton}

object PaneStock {
  final val PaneName = "PaneStock"
  final val ScrollableElementId = "scrollable-stock-with-traversals"
  final val PinnedCotosBodyId = "pinned-cotos-body"

  def apply(
      model: Model,
      uiState: UiState,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val sectionTraversals = SectionTraversals(
      model.traversals,
      dispatch
    )(model)
    section(
      className := optionalClasses(
        Seq(
          ("stock", true),
          ("with-traversals-opened", sectionTraversals.isDefined)
        )
      )
    )(
      Fragment(
        model.domain.currentCotonoma.map(
          sectionCatalog(uiState, _, dispatch)(model)
        ),
        sectionTraversals
      ) match {
        case fragment =>
          if (sectionTraversals.isDefined) {
            ScrollArea(
              scrollableElementId = Some(ScrollableElementId),
              autoHide = true,
              bottomThreshold = None,
              onScrollToBottom = () => ()
            )(fragment)
          } else {
            fragment
          }
      }
    )
  }

  def sectionCatalog(
      uiState: UiState,
      currentCotonoma: Cotonoma,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
    val pinnedCotos = context.domain.pinnedCotos
    section(className := "coto-catalog")(
      Option.when(!pinnedCotos.isEmpty)(
        sectionPinnedCotos(
          pinnedCotos,
          uiState,
          currentCotonoma,
          dispatch
        )
      )
    )
  }

  def sectionPinnedCotos(
      pinned: Seq[(Link, Coto)],
      uiState: UiState,
      currentCotonoma: Cotonoma,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
    val inColumns = uiState.isPinnedInColumns(currentCotonoma.id)
    section(className := "pinned-cotos header-and-body")(
      header(className := "tools")(
        ToolButton(
          classes = optionalClasses(
            Seq(
              ("view-columns", true),
              ("selected", inColumns)
            )
          ),
          tip = "Columns",
          symbol = "view_column",
          disabled = inColumns,
          onClick =
            (() => dispatch(AppMsg.SwitchPinnedView(currentCotonoma.id, true)))
        ),
        ToolButton(
          classes = optionalClasses(
            Seq(
              ("view-document", true),
              ("selected", !inColumns)
            )
          ),
          tip = "Document",
          symbol = "view_agenda",
          disabled = !inColumns,
          onClick =
            (() => dispatch(AppMsg.SwitchPinnedView(currentCotonoma.id, false)))
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
        ScrollArea(
          scrollableElementId = None,
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => ()
        )(
          if (inColumns)
            olPinnedCotos(pinned, inColumns, dispatch)
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
        olPinnedCotos(props.pinned, false, props.dispatch)(props.context),
        divToc(props.pinned, props.dispatch)(props.context)
      )
    }
  }

  private def olPinnedCotos(
      pinned: Seq[(Link, Coto)],
      inColumns: Boolean,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    ol(className := "pinned-cotos")(
      pinned.map { case (pin, coto) =>
        liPinnedCoto(pin, coto, inColumns, dispatch)
      }: _*
    )

  def elementIdOfPinnedCoto(pin: Link): String = s"pin-${pin.id.uuid}"

  private def liPinnedCoto(
      pin: Link,
      coto: Coto,
      inColumn: Boolean,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
    li(
      key := pin.id.uuid,
      className := "pin",
      id := elementIdOfPinnedCoto(pin)
    )(
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id).filter(_._2.id != pin.id),
        dispatch
      ),
      article(
        className := optionalClasses(
          Seq(
            ("pinned-coto", true),
            ("coto", true),
            ("has-children", coto.outgoingLinks > 0)
          )
        )
      )(
        header()(
          ViewCoto.divClassifiedAs(coto, dispatch)
        ),
        div(className := "body")(
          ToolButton(
            classes = "unpin",
            tip = "Unpin",
            tipPlacement = "right",
            symbol = "push_pin"
          ),
          ViewCoto.divContent(coto, dispatch)
        )
      ),
      olSubCotos(coto, inColumn, dispatch)
    )
  }

  private def elementIdOfTocEntry(pin: Link): String =
    s"toc-${elementIdOfPinnedCoto(pin)}"

  private def divToc(
      pinned: Seq[(Link, Coto)],
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    div(className := "toc")(
      ScrollArea(
        scrollableElementId = None,
        autoHide = true,
        bottomThreshold = None,
        onScrollToBottom = () => ()
      )(
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
      inColumn: Boolean,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement = {
    val subCotos = context.domain.childrenOf(coto.id)
    ol(className := "sub-cotos")(
      if (subCotos.size < coto.outgoingLinks)
        div(className := "links")(
          if (context.domain.graphLoading.contains(coto.id)) {
            div(
              className := "loading",
              aria - "busy" := "true"
            )()
          } else {
            ToolButton(
              classes = "fetch-links",
              tip = "Display links",
              tipPlacement = "bottom",
              symbol = "view_headline",
              onClick = (
                  () => dispatch(Domain.Msg.FetchGraphFromCoto(coto.id).toApp)
              )
            )
          }
        )
      else
        subCotos.map { case (link, subCoto) =>
          liSubCoto(link, subCoto, dispatch)
        }
    ) match {
      case olSubCotos =>
        if (inColumn) {
          div(className := "scrollable-sub-cotos")(
            ScrollArea(
              scrollableElementId = None,
              autoHide = true,
              bottomThreshold = None,
              onScrollToBottom = () => ()
            )(olSubCotos)
          )
        } else {
          olSubCotos
        }
    }
  }

  private def liSubCoto(
      link: Link,
      coto: Coto,
      dispatch: AppMsg => Unit
  )(implicit context: Context): ReactElement =
    li(key := link.id.uuid, className := "sub")(
      ViewCoto.ulParents(
        context.domain.parentsOf(coto.id).filter(_._2.id != link.id),
        dispatch
      ),
      article(className := "sub-coto coto")(
        header()(
          ToolButton(
            classes = "unlink",
            tip = "Unlink",
            tipPlacement = "right",
            symbol = "subdirectory_arrow_right"
          ),
          ViewCoto.divClassifiedAs(coto, dispatch)
        ),
        div(className := "body")(
          ViewCoto.divContent(coto, dispatch),
          ViewCoto.divLinksTraversal(coto, "left", dispatch)
        )
      )
    )
}
