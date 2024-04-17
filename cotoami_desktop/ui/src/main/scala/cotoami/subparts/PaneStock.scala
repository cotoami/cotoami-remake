package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Model, Msg}
import cotoami.backend.{Coto, Link}
import cotoami.components.{optionalClasses, ToolButton}

object PaneStock {
  val PaneName = "PaneStock"

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "stock")(
      section(className := "coto-catalog")(
        Option.when(!model.domain.pinnedCotos.isEmpty)(
          sectionPinned(model.domain.pinnedCotos, model, dispatch)
        )
      )
    )

  def sectionPinned(
      pinned: Seq[(Link, Coto)],
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "pinned header-and-body")(
      header(className := "tools")(
        ToolButton(
          classes = "view-columns",
          tip = "Columns",
          symbol = "view_column"
        ),
        ToolButton(
          classes = "view-document selected",
          tip = "Document",
          symbol = "view_agenda"
        )
      ),
      ol(
        className := optionalClasses(
          Seq(
            ("pinned-cotos", true),
            ("body", true),
            ("document-view", true)
          )
        )
      )(
        pinned.map { case (link, coto) =>
          liPinnedCoto(link, coto, model, dispatch)
        }: _*
      )
    )

  private def liPinnedCoto(
      pin: Link,
      coto: Coto,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement = {
    val subCotos = model.domain.subCotosOf(coto.id)
    li(key := pin.id.uuid)(
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
    li(key := link.id.uuid)(
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
