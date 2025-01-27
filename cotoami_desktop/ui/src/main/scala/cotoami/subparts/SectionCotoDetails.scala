package cotoami.subparts

import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.{Into, Msg => AppMsg}
import cotoami.Context
import cotoami.models.{Coto, Link}
import cotoami.components.{optionalClasses, toolButton, ScrollArea}

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
            context.domain.parentsOf(coto.id),
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
    div(className := "insert-linked-coto")(
      toolButton(
        symbol = "add_circle",
        tip = Some("Write a linked coto"),
        tipPlacement = "bottom",
        classes = "insert-linked-coto"
      )
    )

  private def articleMainCoto(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val domain = context.domain
    article(
      className := optionalClasses(
        ViewCoto.commonArticleClasses(coto) ++
          Seq(("main-coto", true))
      )
    )(
      ToolbarCoto(coto),
      header()(
        ViewCoto.divAttributes(coto),
        ViewCoto.addressAuthor(coto, domain.nodes)
      ),
      div(className := "body")(
        ViewCoto.divContent(coto, true),
        Option.when(coto.isCotonoma) {
          ViewCoto.sectionCotonomaContent(coto)
        }
      ),
      ViewCoto.articleFooter(coto)
    )
  }

  private def olSubCotos(coto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
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
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val domain = context.domain
    li(key := link.id.uuid, className := "sub")(
      divAddSubCoto,
      div(className := "sub")(
        ViewCoto.ulParents(
          domain.parentsOf(coto.id).filter(_._2.id != link.id),
          AppMsg.FocusCoto(_)
        ),
        article(
          className := optionalClasses(
            ViewCoto.commonArticleClasses(coto) ++
              Seq(("sub-coto", true))
          ),
          onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(coto.id)))
        )(
          ToolbarCoto(coto),
          header()(
            toolButton(
              symbol = "subdirectory_arrow_right",
              tip = Some("Unlink"),
              tipPlacement = "right",
              classes = "unlink"
            ),
            ViewCoto.divAttributes(coto),
            Option.when(Some(coto.postedById) != domain.nodes.operatingId) {
              ViewCoto.addressAuthor(coto, domain.nodes)
            }
          ),
          div(className := "body")(
            ViewCoto.divContent(coto)
          ),
          ViewCoto.articleFooter(coto)
        ),
        ViewCoto.divLinksTraversal(coto, "bottom")
      )
    )
  }
}
