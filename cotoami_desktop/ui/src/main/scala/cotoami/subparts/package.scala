package cotoami

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.components.{
  material_symbol,
  node_img,
  optionalClasses,
  paneToggle,
  SplitPane
}

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
        .nodes
        .current
        .map(node =>
          section(className := "location")(
            a(
              className := "node-home",
              title := node.name,
              onClick := ((e) => {
                e.preventDefault()
                dispatch(SelectNode(node.id))
              })
            )(node_img(node)),
            model.cotonomas.selected.map(cotonoma =>
              Fragment(
                material_symbol("chevron_right", "arrow"),
                h1(className := "current-cotonoma")(cotonoma.name)
              )
            )
          )
        )
    )

  def appBodyContent(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): Seq[ReactElement] = Seq(
    subparts.NavNodes.view(model, uiState, dispatch),
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
        model.nodes.current.map(NavCotonomas.view(model, _, dispatch))
      ),
      SplitPane.Secondary(className = None)(
        slinky.web.html.main()(
          flowPane(model, uiState, dispatch)
        )
      )
    )
  )

  private val FlowInputEditorName = "flow-input-editor"
  private val FlowInputEditorDefaultHeight = 150

  private def flowPane(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): ReactElement =
    section(className := "flow pane")(
      paneToggle("flow", dispatch),
      (model.nodes.operating, model.currentCotonoma) match {
        case (Some(node), Some(cotonoma)) =>
          Some(
            subparts.CotoInput.view(
              model.flowInput,
              node,
              cotonoma,
              uiState.paneSizes.getOrElse(
                FlowInputEditorName,
                FlowInputEditorDefaultHeight
              ),
              (newSize) => dispatch(ResizePane(FlowInputEditorName, newSize)),
              subMsg => dispatch(FlowInputMsg(subMsg))
            )
          )
        case _ => None
      },
      section(className := "timeline header-and-body")(
      )
    )

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
              material_symbol(entry.level.icon),
              entry.message
            )
          )
        )
    )

  def modal(model: Model, dispatch: Msg => Unit): Option[ReactElement] =
    if (model.nodes.local.isEmpty) {
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
}
