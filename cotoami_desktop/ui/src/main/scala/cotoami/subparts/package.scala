package cotoami

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.components.{optionalClasses, SplitPane}
import cotoami.backend.Node

package object subparts {

  def appBodyContent(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): Seq[ReactElement] = Seq(
    subparts.NavNodes(model, uiState, dispatch),
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
      SplitPane.Primary(
        className = Some(
          optionalClasses(
            Seq(
              ("pane", true),
              ("folded", !uiState.paneOpened(NavCotonomas.PaneName))
            )
          )
        ),
        onClick = Option.when(!uiState.paneOpened(NavCotonomas.PaneName)) {
          () => dispatch(OpenOrClosePane(NavCotonomas.PaneName, true))
        }
      )(
        paneToggle(NavCotonomas.PaneName, dispatch),
        model.domain.nodes.current.map(
          NavCotonomas(model, _, dispatch)
        )
      ),
      SplitPane.Secondary(className = None, onClick = None)(
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
        SplitPane.Primary(
          className = Some(
            optionalClasses(
              Seq(
                ("flow", true),
                ("pane", true),
                ("folded", !flowOpened),
                ("occupying", flowOpened && !stockOpened)
              )
            )
          ),
          onClick = Option.when(!flowOpened) { () =>
            dispatch(OpenOrClosePane(PaneFlow.PaneName, true))
          }
        )(
          Option.when(stockOpened) {
            paneToggle(PaneFlow.PaneName, dispatch)
          },
          PaneFlow(model, uiState, dispatch)
        ),
        SplitPane.Secondary(
          className = Some(
            optionalClasses(
              Seq(
                ("stock", true),
                ("pane", true),
                ("folded", !stockOpened),
                ("occupying", !flowOpened && stockOpened)
              )
            )
          ),
          onClick = Option.when(!stockOpened) { () =>
            dispatch(OpenOrClosePane(PaneStock.PaneName, true))
          }
        )(
          Option.when(flowOpened) {
            paneToggle(PaneStock.PaneName, dispatch, ToRight)
          },
          PaneStock(model, uiState, dispatch)
        )
      )
    )
  }

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

  sealed trait CollapseDirection
  case object ToLeft extends CollapseDirection
  case object ToRight extends CollapseDirection

  def paneToggle(
      paneName: String,
      dispatch: Msg => Unit,
      direction: CollapseDirection = ToLeft
  ): ReactElement =
    div(className := "pane-toggle")(
      button(
        className := "fold default",
        title := "Fold",
        onClick := (_ => dispatch(OpenOrClosePane(paneName, false)))
      )(
        span(className := "material-symbols")(
          direction match {
            case ToLeft  => "arrow_left"
            case ToRight => "arrow_right"
          }
        )
      ),
      button(
        className := "unfold default",
        title := "Unfold",
        onClick := (_ => dispatch(OpenOrClosePane(paneName, true)))
      )(
        span(className := "material-symbols")(
          direction match {
            case ToLeft  => "arrow_right"
            case ToRight => "arrow_left"
          }
        )
      )
    )
}
