package cotoami

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.components.{
  materialSymbol,
  optionalClasses,
  paneToggle,
  SplitPane,
  ToRight
}
import cotoami.backend.Node

package object subparts {

  def appHeader(model: Model, dispatch: Msg => Unit): ReactElement =
    header(
      button(
        className := "app-info default",
        title := "View app info"
      )(
        img(
          className := "app-icon",
          alt := "Cotoami",
          src := "/images/logo/logomark.svg"
        )
      ),
      model
        .domain
        .location
        .map { case (node, cotonoma) =>
          section(className := "location")(
            a(
              className := "node-home",
              title := node.name,
              onClick := ((e) => {
                e.preventDefault()
                dispatch(SelectNode(node.id))
              })
            )(nodeImg(node)),
            cotonoma.map(cotonoma =>
              Fragment(
                materialSymbol("chevron_right", "arrow"),
                h1(className := "current-cotonoma")(cotonoma.name)
              )
            )
          )
        }
    )

  def appBodyContent(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): Seq[ReactElement] = Seq(
    subparts.NavNodes.view(model.domain.nodes, uiState, dispatch),
    SplitPane(
      vertical = true,
      initialPrimarySize = uiState.paneSizes.getOrElse(
        NavCotonomas.PaneName,
        NavCotonomas.DefaultWidth
      ),
      resizable = uiState.paneOpened(NavCotonomas.PaneName),
      className = Some("node-contents"),
      onPrimarySizeChanged = (
          (newSize) => dispatch(ResizePane(NavCotonomas.PaneName, newSize))
      )
    )(
      SplitPane.Primary(className =
        Some(
          optionalClasses(
            Seq(
              ("pane", true),
              ("folded", !uiState.paneOpened(NavCotonomas.PaneName))
            )
          )
        )
      )(
        paneToggle(NavCotonomas.PaneName, dispatch),
        model.domain.nodes.current.map(
          NavCotonomas.view(model, _, dispatch)
        )
      ),
      SplitPane.Secondary(className = None)(
        flowAndStock(model, uiState, dispatch)
      )
    )
  )

  def flowAndStock(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement = {
    val flowOpened = uiState.paneOpened(PaneFlow.PaneName)
    val stockOpened = uiState.paneOpened(PaneStock.PaneName)
    slinky.web.html.main()(
      SplitPane(
        vertical = true,
        initialPrimarySize = uiState.paneSizes.getOrElse(
          PaneFlow.PaneName,
          PaneFlow.DefaultWidth
        ),
        resizable = flowOpened && stockOpened,
        className = Some("main"),
        onPrimarySizeChanged = (
            (newSize) => dispatch(ResizePane(PaneFlow.PaneName, newSize))
        )
      )(
        SplitPane.Primary(className =
          Some(
            optionalClasses(
              Seq(
                ("flow", true),
                ("pane", true),
                ("folded", !flowOpened),
                ("occupying", flowOpened && !stockOpened)
              )
            )
          )
        )(
          Option.when(stockOpened) {
            paneToggle(PaneFlow.PaneName, dispatch)
          },
          PaneFlow.view(model, uiState, dispatch)
        ),
        SplitPane.Secondary(className =
          Some(
            optionalClasses(
              Seq(
                ("stock", true),
                ("pane", true),
                ("folded", !stockOpened),
                ("occupying", !flowOpened && stockOpened)
              )
            )
          )
        )(
          Option.when(flowOpened) {
            paneToggle(PaneStock.PaneName, dispatch, ToRight)
          },
          PaneStock.view(model, uiState, dispatch)
        )
      )
    )
  }

  def appFooter(model: Model, dispatch: Msg => Unit): ReactElement =
    footer(
      div(className := "browser-nav")(
        div(className := "path")(model.path)
      ),
      model.log
        .lastEntry()
        .map(entry =>
          div(className := s"log-peek ${entry.level.name}")(
            button(
              className := "open-log-view default",
              onClick := ((e) => dispatch(cotoami.ToggleLogView))
            )(
              materialSymbol(entry.level.icon),
              entry.message
            )
          )
        )
    )

  def modal(model: Model, dispatch: Msg => Unit): Option[ReactElement] =
    if (model.domain.nodes.local.isEmpty) {
      model.systemInfo.map(info =>
        ModalWelcome
          .view(
            model.modalWelcome,
            info.recent_databases.toSeq,
            dispatch
          )
      )
    } else {
      None
    }

  def nodeImg(node: Node): ReactElement =
    img(
      className := "node-icon",
      alt := node.name,
      src := s"data:image/png;base64,${node.icon}"
    )
}
