package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Ito, OrderContext, Siblings}
import cotoami.repository.Root

package object pins {

  def elementIdOfPin(pin: Ito): String = s"pin-${pin.id.uuid}"

  def sectionPinnedCotos(pins: Siblings)(
      renderSubCotos: Siblings => ReactElement
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsIto.sectionSiblings(pins, "pinned-cotos") { case (pin, coto, order) =>
      sectionPinnedCoto(pin, coto, order)(renderSubCotos)
    }

  def sectionPinnedCoto(
      pin: Ito,
      coto: Coto,
      order: OrderContext
  )(
      renderSubCotos: Siblings => ReactElement
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val subCotos = context.repo.childrenOf(coto.id)
    section(
      className := optionalClasses(
        Seq(
          ("pin", true),
          ("with-description", pin.description.isDefined)
        )
      ),
      id := elementIdOfPin(pin)
    )(
      PartsCoto.article(
        coto,
        dispatch,
        Seq(
          ("pinned-coto", true),
          ("has-children", context.repo.itos.anyFrom(coto.id))
        )
      )(
        PartsIto.buttonPin(pin),
        ToolbarCoto(coto),
        ToolbarReorder(pin, order),
        PartsCoto.ulParents(
          context.repo.parentsOf(coto.id).filter(_._2.id != pin.id),
          SectionTraversals.Msg.OpenTraversal(_).into
        ),
        div(className := "body")(
          PartsCoto.divContent(coto)
        ),
        footer()(
          PartsCoto.divAttributes(coto)
        )
      ),
      if (coto.isCotonoma && !context.repo.alreadyLoadedGraphFrom(coto.id)) {
        div(className := "itos-not-yet-loaded")(
          if (context.repo.graphLoading.contains(coto.id)) {
            div(
              className := "loading",
              aria - "busy" := "true"
            )()
          } else {
            toolButton(
              symbol = "more_horiz",
              tip = Some(context.i18n.text.LoadItos),
              tipPlacement = "bottom",
              classes = "fetch-itos",
              onClick = _ => dispatch(Root.Msg.FetchGraphFromCoto(coto.id))
            )
          }
        )
      } else {
        subCotos.map(renderSubCotos)
      }
    )
  }

  def sectionSubCotos(
      subCotos: Siblings
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsIto.sectionSiblings(subCotos, "sub-cotos") { case (ito, coto, order) =>
      div(className := "sub-coto")(
        PartsCoto.article(coto, dispatch, Seq(("sub-coto", true)))(
          ToolbarCoto(coto),
          ToolbarReorder(ito, order),
          PartsIto.buttonSubcotoIto(ito),
          PartsCoto.ulParents(
            context.repo.parentsOf(coto.id).filter(_._2.id != ito.id),
            SectionTraversals.Msg.OpenTraversal(_).into
          ),
          div(className := "body")(
            PartsCoto.divContent(coto),
            divItosTraversalButton(coto)
          ),
          footer()(
            PartsCoto.divAttributes(coto)
          )
        )
      )
    }

  def divItosTraversalButton(
      coto: Coto
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] =
    Option.when(context.repo.itos.anyFrom(coto.id)) {
      div(className := "has-itos")(
        toolButton(
          symbol = "arrow_forward",
          classes = "open-traversal",
          onClick = e => {
            e.stopPropagation()
            dispatch(SectionTraversals.Msg.OpenTraversal(coto.id))
          }
        )
      )
    }
}
