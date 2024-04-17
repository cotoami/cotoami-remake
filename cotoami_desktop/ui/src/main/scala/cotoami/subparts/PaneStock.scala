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
          pinned(model.domain.pinnedCotos, model, dispatch)
        )
      )
    )

  def pinned(
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
          li(key := link.id.uuid)(
            pinnedCoto(coto, model, dispatch)
          )
        }: _*
      )
    )

  private def pinnedCoto(
      coto: Coto,
      model: Model,
      dispatch: Msg => Unit
  ): ReactElement =
    article(
      className := optionalClasses(
        Seq(
          ("pinned-coto", true),
          ("coto", true),
          ("has-children", false)
        )
      )
    )(
      ToolButton(
        classes = "unpin",
        tip = "Unpin",
        tipPlacement = "right",
        symbol = "push_pin"
      ),
      header()(
        ViewCoto.otherCotonomas(coto, model.domain, dispatch)
      ),
      div(className := "body")(
        ViewCoto.content(coto, s"pinned-${coto.id}", model, dispatch)
      )
    )
}
