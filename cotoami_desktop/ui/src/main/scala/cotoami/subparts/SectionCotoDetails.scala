package cotoami.subparts

import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.{toolButton, ScrollArea}
import cotoami.{Into, Msg => AppMsg}
import cotoami.Context
import cotoami.models.{Coto, Cotonoma, Id, Siblings}

object SectionCotoDetails {

  def apply(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "coto-details header-and-body")(
      header(
        toolButton(
          classes = "back",
          symbol = "arrow_back",
          tip = Some(context.i18n.text.Back),
          tipPlacement = "right",
          onClick = _ => dom.window.history.back()
        ),
        toolButton(
          classes = "close",
          symbol = "close",
          onClick = _ => dispatch(AppMsg.UnfocusCoto)
        )
      ),
      div(className := "body")(
        ScrollArea()(
          articleMainCoto(coto),
          context.repo.childrenOf(coto.id).map(sectionSubCotos)
        ).withKey(coto.id.uuid) // Reset the state when the coto is changed
      )
    )

  private def divInsertSubCoto(
      sourceCotoId: Id[Coto],
      order: Option[Int],
      defaultCotonomaId: Option[Id[Cotonoma]]
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "insert-sub-coto")(
      toolButton(
        symbol = "add_circle",
        tip = Some(context.i18n.text.Insert),
        tipPlacement = "bottom",
        classes = "insert-sub-coto",
        onClick = e =>
          dispatch(
            Modal.Msg.OpenModal(
              Modal.Subcoto(sourceCotoId, order, defaultCotonomaId)
            )
          )
      )
    )

  private def articleMainCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    PartsCoto.article(coto, dispatch, Seq(("main-coto", true)))(
      ToolbarCoto(coto),
      PartsCoto.ulParents(
        context.repo.parentsOf(coto.id),
        AppMsg.FocusCoto(_)
      ),
      header()(
        PartsCoto.divAttributes(coto),
        PartsCoto.addressAuthor(coto)
      ),
      div(className := "body")(
        PartsCoto.divContent(coto, true)
      ),
      PartsCoto.articleFooter(coto)
    )

  private def sectionSubCotos(
      subCotos: Siblings
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsIto.sectionSiblings(subCotos, "sub-cotos") { case (ito, coto, order) =>
      val repo = context.repo
      val addSubCotoTo =
        // In a sibling group that doesn't belong to the same node of the
        // parent coto (non-main group), the default target cotonoma of a new
        // sibling will be the same as that of the neighboring sibling coto.
        if (ito.nodeId != subCotos.parent.nodeId)
          coto.postedInId
        else
          None
      div(className := "sub")(
        divInsertSubCoto(ito.sourceCotoId, Some(ito.order), addSubCotoTo),
        div(className := "sub-coto")(
          PartsCoto.article(coto, dispatch, Seq(("sub-coto", true)))(
            ToolbarCoto(coto),
            ToolbarReorder(ito, order),
            PartsIto.buttonSubcotoIto(ito),
            PartsCoto.ulParents(
              repo.parentsOf(coto.id).filter(_._2.id != ito.id),
              AppMsg.FocusCoto(_)
            ),
            header()(
              PartsCoto.divAttributes(coto),
              PartsCoto.addressAuthor(coto)
            ),
            div(className := "body")(
              PartsCoto.divContent(coto)
            ),
            PartsCoto.articleFooter(coto),
            div(className := "padding-bottom")(
              PartsCoto.divDetailsButton(coto)
            )
          )
        ),
        Option.when(order.isLast) {
          divInsertSubCoto(ito.sourceCotoId, None, addSubCotoTo)
        }
      )
    }
}
