package cotoami.subparts

import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.{toolButton, ScrollArea}
import cotoami.{Into, Msg => AppMsg}
import cotoami.Context
import cotoami.models.{Coto, Id, Siblings}

object SectionCotoDetails {

  def apply(
      coto: Coto
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "coto-details header-and-body")(
      header(
        toolButton(
          classes = "back",
          symbol = "arrow_back",
          tip = Some("Back"),
          tipPlacement = "right",
          onClick = _ => dom.window.history.back()
        ),
        toolButton(
          classes = "close",
          symbol = "close",
          tip = Some("Close"),
          tipPlacement = "left",
          onClick = _ => dispatch(AppMsg.UnfocusCoto)
        )
      ),
      div(className := "body")(
        ScrollArea()(
          PartsCoto.ulParents(
            context.repo.parentsOf(coto.id),
            AppMsg.FocusCoto(_)
          ),
          articleMainCoto(coto),
          div(className := "sub-cotos")(
            context.repo.childrenOf(coto.id).map(sectionSubCotos),
            divAddSubCoto(coto.id, None)
          )
        ).withKey(coto.id.uuid) // Reset the state when the coto is changed
      )
    )

  private def divAddSubCoto(sourceCotoId: Id[Coto], order: Option[Int])(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "insert-sub-coto")(
      toolButton(
        symbol = "add_circle",
        tip = Some(context.i18n.text.WriteSubcoto),
        tipPlacement = "bottom",
        classes = "insert-sub-coto",
        onClick = e =>
          dispatch(
            Modal.Msg.OpenModal(
              Modal.Subcoto(sourceCotoId, order, context.repo)
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
      header()(
        PartsCoto.divAttributes(coto),
        PartsCoto.addressAuthor(coto, context.repo.nodes)
      ),
      div(className := "body")(
        PartsCoto.divContent(coto, true),
        Option.when(coto.isCotonoma) {
          PartsCoto.sectionCotonomaContent(coto)
        }
      ),
      PartsCoto.articleFooter(coto)
    )

  private def sectionSubCotos(
      subCotos: Siblings
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsIto.sectionSiblings(subCotos, "sub-cotos") { case (ito, coto, order) =>
      val repo = context.repo
      div(className := "sub")(
        divAddSubCoto(ito.sourceCotoId, Some(ito.order)),
        div(className := "sub")(
          PartsCoto.ulParents(
            repo.parentsOf(coto.id).filter(_._2.id != ito.id),
            AppMsg.FocusCoto(_)
          ),
          PartsCoto.article(coto, dispatch, Seq(("sub-coto", true)))(
            ToolbarCoto(coto),
            ToolbarReorder(ito, order),
            header()(
              PartsIto.buttonSubcotoIto(ito),
              PartsCoto.divAttributes(coto),
              Option.when(!repo.nodes.isSelf(coto.postedById)) {
                PartsCoto.addressAuthor(coto, repo.nodes)
              }
            ),
            div(className := "body")(
              PartsCoto.divContent(coto)
            ),
            PartsCoto.articleFooter(coto)
          ),
          PartsCoto.divItosTraversal(coto, "bottom")
        )
      )
    }
}
