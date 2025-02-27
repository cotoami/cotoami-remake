package cotoami.subparts

import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Into, Msg => AppMsg}
import cotoami.Context
import cotoami.models.{Coto, Ito, OrderContext}
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
          ViewCoto.ulParents(
            context.repo.parentsOf(coto.id),
            AppMsg.FocusCoto(_)
          ),
          articleMainCoto(coto),
          div(className := "sub-cotos")(
            olSubCotos(coto),
            divAddSubCoto
          )
        ).withKey(coto.id.uuid) // Reset the state when the coto is changed
      )
    )

  private def divAddSubCoto: ReactElement =
    div(className := "insert-sub-coto")(
      toolButton(
        symbol = "add_circle",
        tip = Some("Write a sub-coto"),
        tipPlacement = "bottom",
        classes = "insert-sub-coto"
      )
    )

  private def articleMainCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    ViewCoto.article(coto, dispatch, Seq(("main-coto", true)))(
      ToolbarCoto(coto),
      header()(
        ViewCoto.divAttributes(coto),
        ViewCoto.addressAuthor(coto, context.repo.nodes)
      ),
      div(className := "body")(
        ViewCoto.divContent(coto, true),
        Option.when(coto.isCotonoma) {
          ViewCoto.sectionCotonomaContent(coto)
        }
      ),
      ViewCoto.articleFooter(coto)
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
      divAddSubCoto,
      div(className := "sub")(
        ViewCoto.ulParents(
          repo.parentsOf(coto.id).filter(_._2.id != ito.id),
          AppMsg.FocusCoto(_)
        ),
        ViewCoto.article(coto, dispatch, Seq(("sub-coto", true)))(
          ToolbarCoto(coto),
          ToolbarReorder(ito, order),
          header()(
            buttonSubcotoIto(ito),
            ViewCoto.divAttributes(coto),
            Option.when(Some(coto.postedById) != repo.nodes.operatingId) {
              ViewCoto.addressAuthor(coto, repo.nodes)
            }
          ),
          div(className := "body")(
            ViewCoto.divContent(coto)
          ),
          ViewCoto.articleFooter(coto)
        ),
        ViewCoto.divItosTraversal(coto, "bottom")
      )
    )
  }
}
