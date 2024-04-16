package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{DeselectCotonoma, Msg, SelectCotonoma}
import cotoami.components.{materialSymbol, optionalClasses, ScrollArea}
import cotoami.backend.{Cotonoma, Node}
import cotoami.repositories.Domain

object NavCotonomas {
  val PaneName = "NavCotonomas"
  val DefaultWidth = 230

  def view(
      domain: Domain,
      currentNode: Node,
      dispatch: Msg => Unit
  ): ReactElement =
    nav(className := "cotonomas header-and-body")(
      header()(
        if (domain.cotonomas.selected.isEmpty) {
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
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => dispatch(Msg.FetchMoreRecentCotonomas)
        )(
          domain.cotonomas.selected.map(
            sectionCurrent(domain, _, dispatch)
          ),
          Option.when(!domain.recentCotonomas.isEmpty)(
            sectionRecent(domain, domain.recentCotonomas, dispatch)
          ),
          div(
            className := "more",
            aria - "busy" := domain.cotonomas.recentLoading.toString()
          )()
        )
      )
    )

  private def sectionCurrent(
      domain: Domain,
      selectedCotonoma: Cotonoma,
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
              liCotonoma(domain, _, dispatch)
            ): _*
          )
        ),
        li(key := "current", className := "current-cotonoma cotonoma selected")(
          cotonomaLabel(domain, selectedCotonoma)
        ),
        li(key := "sub")(
          ul(className := "sub-cotonomas")(
            domain.cotonomas.subs.map(
              liCotonoma(domain, _, dispatch)
            ) ++ Option.when(
              domain.cotonomas.subIds.nextPageIndex.isDefined
            )(
              li(key := "more-button")(
                button(
                  className := "more-sub-cotonomas default",
                  onClick := ((e) =>
                    dispatch(Msg.FetchMoreSubCotonomas(selectedCotonoma.id))
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
      domain: Domain,
      cotonomas: Seq[Cotonoma],
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "recent")(
      h2()("Recent"),
      ul()(cotonomas.map(liCotonoma(domain, _, dispatch)): _*)
    )

  private def liCotonoma(
      domain: Domain,
      cotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    li(
      className := optionalClasses(
        Seq(("selected", domain.cotonomas.isSelecting(cotonoma.id)))
      ),
      key := cotonoma.id.uuid
    )(
      if (domain.cotonomas.isSelecting(cotonoma.id)) {
        span(className := "cotonoma")(cotonomaLabel(domain, cotonoma))
      } else {
        a(
          className := "cotonoma",
          title := cotonoma.name,
          onClick := ((e) => {
            e.preventDefault()
            dispatch(SelectCotonoma(cotonoma.id))
          })
        )(
          cotonomaLabel(domain, cotonoma)
        )
      }
    )

  private def cotonomaLabel(domain: Domain, cotonoma: Cotonoma): ReactElement =
    Fragment(
      domain.nodes.get(cotonoma.nodeId).map(nodeImg),
      cotonoma.name
    )
}
