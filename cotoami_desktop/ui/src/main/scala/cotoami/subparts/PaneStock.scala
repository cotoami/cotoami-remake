package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg, SwitchPinnedView}
import cotoami.backend.{Coto, Cotonoma, Link}
import cotoami.components.{optionalClasses, ScrollArea, ToolButton}

object PaneStock {
  val PaneName = "PaneStock"
  val ScrollableElementId = "scrollable-stock-with-traversals"

  def apply(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement = {
    val sectionTraversals = SectionTraversals(
      model.traversals,
      model.openedCotoViews,
      model.domain,
      dispatch
    )
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
          sectionCatalog(model, uiState, _, dispatch)
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
      model: Model,
      uiState: Model.UiState,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "coto-catalog")(
      Option.when(!model.domain.pinnedCotos.isEmpty)(
        sectionPinned(
          model.domain.pinnedCotos,
          model,
          uiState,
          currentCotonoma,
          dispatch
        )
      )
    )

  def sectionPinned(
      pinned: Seq[(Link, Coto)],
      model: Model,
      uiState: Model.UiState,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement = {
    val inColumns = uiState.isPinnedInColumns(currentCotonoma.id)
    section(className := "pinned header-and-body")(
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
          onClick = (() => dispatch(SwitchPinnedView(currentCotonoma.id, true)))
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
            (() => dispatch(SwitchPinnedView(currentCotonoma.id, false)))
        )
      ),
      div(
        className := optionalClasses(
          Seq(
            ("body", true),
            ("document-view", !inColumns),
            ("column-view", inColumns)
          )
        )
      )(
        ScrollArea(
          scrollableElementId = None,
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => ()
        )(
          ol(className := "pinned-cotos")(
            pinned.map { case (link, coto) =>
              liPinnedCoto(link, coto, inColumns, model, dispatch)
            }: _*
          )
        )
      )
    )
  }

  private def liPinnedCoto(
      pin: Link,
      coto: Coto,
      inColumn: Boolean,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement = {
    val subCotos = model.domain.childrenOf(coto.id)
    li(key := pin.id.uuid, className := "pin")(
      article(
        className := optionalClasses(
          Seq(
            ("pinned-coto", true),
            ("coto", true),
            ("has-children", !subCotos.isEmpty)
          )
        )
      )(
        header()(
          ViewCoto.otherCotonomas(coto, model.domain, dispatch)
        ),
        div(className := "body")(
          ToolButton(
            classes = "unpin",
            tip = "Unpin",
            tipPlacement = "right",
            symbol = "push_pin"
          ),
          ViewCoto.content(
            coto,
            s"pinned-${coto.id}",
            model.openedCotoViews,
            model.domain,
            dispatch
          )
        )
      ),
      ol(className := "sub-cotos")(
        subCotos.map { case (link, subCoto) =>
          liSubCoto(link, subCoto, model, dispatch)
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
    )
  }

  private def liSubCoto(
      link: Link,
      coto: Coto,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    li(key := link.id.uuid, className := "sub")(
      article(className := "sub-coto coto")(
        header()(
          ToolButton(
            classes = "unlink",
            tip = "Unlink",
            tipPlacement = "right",
            symbol = "subdirectory_arrow_right"
          ),
          ViewCoto.otherCotonomas(coto, model.domain, dispatch)
        ),
        div(className := "body")(
          ViewCoto.content(
            coto,
            s"pinned-${coto.id}",
            model.openedCotoViews,
            model.domain,
            dispatch
          ),
          Option.when(coto.outgoingLinks > 0) {
            div(className := "links")(
              ToolButton(
                classes = "open-traversal",
                tip = "Links",
                tipPlacement = "left",
                symbol = "view_headline",
                onClick = (() => dispatch(Msg.OpenTraversal(coto.id)))
              )
            )
          }
        )
      )
    )
}
