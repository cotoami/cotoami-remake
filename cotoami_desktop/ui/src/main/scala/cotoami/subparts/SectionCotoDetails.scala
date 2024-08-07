package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Msg => AppMsg}
import cotoami.Context
import cotoami.backend.Coto
import cotoami.components.toolButton

object SectionCotoDetails {

  def apply(
      coto: Coto
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    section(className := "coto-details")(
      header(
        toolButton(
          symbol = "arrow_back",
          tip = "Back to list",
          tipPlacement = "right",
          classes = "back",
          onClick = _ => dispatch(AppMsg.UnfocusCoto)
        )
      ),
      articleCotoMain(coto)
    )

  private def articleCotoMain(coto: Coto)(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val domain = context.domain
    article(className := "coto main")(
      header()(
        ViewCoto.divClassifiedAs(coto),
        ViewCoto.addressAuthor(coto, domain.nodes)
      ),
      div(className := "body")(
        ViewCoto.divContent(coto, true)
      ),
      footer()(
        time(
          className := "posted-at",
          title := context.time.formatDateTime(coto.createdAt)
        )(
          context.time.display(coto.createdAt)
        )
      )
    )
  }
}
