package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Msg => AppMsg}
import cotoami.Context
import cotoami.backend.{Coto, Link}
import cotoami.components.{toolButton, ScrollArea}

object SectionCotoDetails {

  def apply(
      coto: Coto
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    section(className := "coto-details header-and-body")(
      header(
        toolButton(
          symbol = "arrow_back",
          tip = "Back to list",
          tipPlacement = "right",
          classes = "back",
          onClick = _ => dispatch(AppMsg.UnfocusCoto)
        )
      ),
      div(className := "body")(
        ScrollArea()(
          ViewCoto.ulParents(
            context.domain.parentsOf(coto.id),
            AppMsg.FocusCoto(_)
          ),
          articleMainCoto(coto),
          div(className := "sub-cotos")(
            olSubCotos(coto)
          )
        )
      )
    )

  private def articleMainCoto(coto: Coto)(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val domain = context.domain
    article(className := "main-coto coto")(
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

  private def olSubCotos(coto: Coto)(implicit
      context: Context,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val subCotos = context.domain.childrenOf(coto.id)
    ol(className := "sub-cotos")(
      subCotos.map { case (link, subCoto) =>
        liSubCoto(link, subCoto)
      }: _*
    )
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
          ViewCoto.divContent(coto)
        )
      ),
      ViewCoto.divLinksTraversal(coto, "bottom")
    )
}
