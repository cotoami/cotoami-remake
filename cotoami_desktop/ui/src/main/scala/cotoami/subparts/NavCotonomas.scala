package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{DeselectCotonoma, Model, Msg, SelectCotonoma}
import cotoami.components.{
  material_symbol,
  node_img,
  optionalClasses,
  paneToggle,
  ScrollArea,
  SplitPane
}
import cotoami.backend.{Cotonoma, Node}

object NavCotonomas {
  val PaneName = "nav-cotonomas"
  val DefaultWidth = 230

  def view(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    SplitPane.Primary(className =
      Some(
        optionalClasses(
          Seq(
            ("pane", true),
            ("folded", !uiState.paneOpened(PaneName))
          )
        )
      )
    )(
      paneToggle(PaneName, dispatch),
      model.nodes.current.map(navCotonomas(model, _, dispatch))
    )

  private def navCotonomas(
      model: Model,
      currentNode: Node,
      dispatch: Msg => Unit
  ): ReactElement = {
    val recentCotonomas = model.recentCotonomasWithoutRoot
    nav(className := "cotonomas header-and-body")(
      header()(
        if (model.cotonomas.selected.isEmpty) {
          div(className := "cotonoma home selected")(
            material_symbol("home"),
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
            material_symbol("home"),
            currentNode.name
          )
        }
      ),
      section(className := "cotonomas body")(
        ScrollArea(
          autoHide = true,
          bottomThreshold = None,
          onScrollToBottom = () => dispatch(cotoami.FetchMoreCotonomas)
        )(
          model.cotonomas.selected.map(sectionCurrent(model, _, dispatch)),
          Option.when(!recentCotonomas.isEmpty)(
            sectionRecent(model, recentCotonomas, dispatch)
          ),
          div(
            className := "more",
            aria - "busy" := model.cotonomasLoading.toString()
          )()
        )
      )
    )
  }

  private def sectionCurrent(
      model: Model,
      selectedCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "current")(
      h2()("Current"),
      ul(
        className := optionalClasses(
          Seq(
            ("has-super-cotonomas", model.cotonomas.anySuperOfSelected)
          )
        )
      )(
        li()(
          ul(className := "super-cotonomas")(
            model.cotonomas.superOfSelected.map(
              liCotonoma(model, _, dispatch)
            ): _*
          )
        ),
        li(className := "current-cotonoma cotonoma selected")(
          cotonomaLabel(model, selectedCotonoma)
        ),
        li()(
          ul(className := "sub-cotonomas")(
            model.cotonomas.subOfSelected.map(
              liCotonoma(model, _, dispatch)
            ): _*
          )
        )
      )
    )

  private def sectionRecent(
      model: Model,
      cotonomas: Seq[Cotonoma],
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "recent")(
      h2()("Recent"),
      ul()(cotonomas.map(liCotonoma(model, _, dispatch)): _*)
    )

  private def liCotonoma(
      model: Model,
      cotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    li(
      className := optionalClasses(
        Seq(("selected", model.cotonomas.isSelecting(cotonoma.id)))
      ),
      key := cotonoma.id.uuid
    )(
      if (model.cotonomas.isSelecting(cotonoma.id)) {
        span(className := "cotonoma")(cotonomaLabel(model, cotonoma))
      } else {
        a(
          className := "cotonoma",
          title := cotonoma.name,
          onClick := ((e) => {
            e.preventDefault()
            dispatch(SelectCotonoma(cotonoma.id))
          })
        )(
          cotonomaLabel(model, cotonoma)
        )
      }
    )

  private def cotonomaLabel(model: Model, cotonoma: Cotonoma): ReactElement =
    Fragment(
      model.nodes.get(cotonoma.nodeId).map(node_img(_)),
      cotonoma.name
    )
}
