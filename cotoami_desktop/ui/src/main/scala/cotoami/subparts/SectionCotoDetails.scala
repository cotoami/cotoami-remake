package cotoami.subparts

import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Into, Msg => AppMsg}
import cotoami.Context
import cotoami.models.{Coto, Id, Ito, OrderContext}
import cotoami.components.{toolButton, Flipped, Flipper, ScrollArea}

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
            olSubCotos(coto),
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

  private def olSubCotos(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val subCotos = context.repo.childrenOf(coto.id)
    Flipper(
      element = "ol",
      className = "sub-cotos",
      flipKey = subCotos.fingerprint
    )(
      subCotos.eachWithOrderContext.map { case (ito, subCoto, order) =>
        Flipped(key = ito.id.uuid, flipId = ito.id.uuid)(
          liSubCoto(ito, subCoto, order)
        ): ReactElement
      }.toSeq: _*
    )
  }

  private def liSubCoto(
      ito: Ito,
      coto: Coto,
      order: OrderContext
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val repo = context.repo
    li(key := ito.id.uuid, className := "sub")(
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
            Option.when(Some(coto.postedById) != repo.nodes.operatingId) {
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
