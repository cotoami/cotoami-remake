package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{DeselectCotonoma, Model, Msg, SelectCotonoma}
import cotoami.components.{materialSymbol, optionalClasses, ScrollArea}
import cotoami.backend.{Cotonoma, Node}
import cotoami.repositories.{Cotonomas, Domain}

object NavCotonomas {
  val PaneName = "NavCotonomas"
  val DefaultWidth = 230

  def apply(
      model: Model,
      currentNode: Node,
      dispatch: Msg => Unit
  ): ReactElement = {
    val cotonomas = model.domain.cotonomas
    nav(className := "cotonomas header-and-body")(
      header()(
        if (cotonomas.selected.isEmpty) {
          div(className := "cotonoma home selected")(
            materialSymbol("home"),
            currentNode.name
          )
        } else {
          a(
            className := "cotonoma home",
            title := s"${currentNode.name} home",
            onClick := ((e) => {
              e.preventDefault()
              dispatch(DeselectCotonoma)
            })
          )(
            materialSymbol("home"),
            currentNode.name
          )
        }
      ),
      section(className := "cotonomas body")(
        ScrollArea(
          scrollableElementId = None,
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => dispatch(Cotonomas.FetchMoreRecent.toAppMsg)
        )(
          cotonomas.selected.map(
            sectionCurrent(_, model.domain, dispatch)
          ),
          Option.when(!model.domain.recentCotonomas.isEmpty)(
            sectionRecent(model.domain.recentCotonomas, model.domain, dispatch)
          ),
          div(
            className := "more",
            aria - "busy" := cotonomas.recentLoading.toString()
          )()
        )
      )
    )
  }

  private def sectionCurrent(
      selectedCotonoma: Cotonoma,
      domain: Domain,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "current")(
      h2()("Current"),
      ul(
        className := optionalClasses(
          Seq(
            ("has-super-cotonomas", domain.superCotonomas.size > 0)
          )
        )
      )(
        li(key := "super")(
          ul(className := "super-cotonomas")(
            domain.superCotonomas.map(
              liCotonoma(_, domain, dispatch)
            ): _*
          )
        ),
        li(key := "current", className := "current-cotonoma cotonoma selected")(
          cotonomaLabel(selectedCotonoma, domain)
        ),
        li(key := "sub")(
          ul(className := "sub-cotonomas")(
            domain.cotonomas.subs.map(
              liCotonoma(_, domain, dispatch)
            ) ++ Option.when(
              domain.cotonomas.subIds.nextPageIndex.isDefined
            )(
              li(key := "more-button")(
                button(
                  className := "more-sub-cotonomas default",
                  onClick := ((e) =>
                    dispatch(
                      Cotonomas.FetchMoreSubs(selectedCotonoma.id).toAppMsg
                    )
                  )
                )(
                  materialSymbol("more_horiz")
                )
              )
            ) ++ Option.when(domain.cotonomas.subsLoading)(
              li(
                key := "more-loading",
                className := "more",
                aria - "busy" := "true"
              )()
            )
          )
        )
      )
    )

  private def sectionRecent(
      cotonomas: Seq[Cotonoma],
      domain: Domain,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "recent")(
      h2()("Recent"),
      ul()(cotonomas.map(liCotonoma(_, domain, dispatch)): _*)
    )

  private def liCotonoma(
      cotonoma: Cotonoma,
      domain: Domain,
      dispatch: Msg => Unit
  ): ReactElement =
    li(
      className := optionalClasses(
        Seq(("selected", domain.cotonomas.isSelecting(cotonoma.id)))
      ),
      key := cotonoma.id.uuid
    )(
      if (domain.cotonomas.isSelecting(cotonoma.id)) {
        span(className := "cotonoma")(cotonomaLabel(cotonoma, domain))
      } else {
        a(
          className := "cotonoma",
          title := cotonoma.name,
          onClick := ((e) => {
            e.preventDefault()
            dispatch(SelectCotonoma(cotonoma.id))
          })
        )(
          cotonomaLabel(cotonoma, domain)
        )
      }
    )

  private def cotonomaLabel(cotonoma: Cotonoma, domain: Domain): ReactElement =
    Fragment(
      domain.nodes.get(cotonoma.nodeId).map(nodeImg),
      cotonoma.name
    )
}
