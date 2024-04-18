package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.backend.{Coto, Cotonoma, Link}
import cotoami.components.{optionalClasses, ScrollArea, ToolButton}

object PaneStock {
  val PaneName = "PaneStock"

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "stock")(
      model.domain.currentCotonoma.map(
        sectionCatalog(model, uiState, _, dispatch)
      )
    )

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
          symbol = "view_column"
        ),
        ToolButton(
          classes = optionalClasses(
            Seq(
              ("view-document", true),
              ("selected", !inColumns)
            )
          ),
          tip = "Document",
          symbol = "view_agenda"
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
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => ()
        )(
          ol(className := "pinned-cotos")(
            pinned.map { case (link, coto) =>
              liPinnedCoto(link, coto, model, dispatch)
            }: _*
          )
        )
      )
    )
  }

  private def liPinnedCoto(
      pin: Link,
      coto: Coto,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement = {
    val subCotos = model.domain.subCotosOf(coto.id)
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
          ViewCoto.content(coto, s"pinned-${coto.id}", model, dispatch)
        )
      ),
      ol(className := "sub-cotos")(
        subCotos.map { case (link, subCoto) =>
          liSubCoto(link, subCoto, model, dispatch)
        }
      )
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
          ViewCoto.content(coto, s"pinned-${coto.id}", model, dispatch)
        )
      )
    )
}
