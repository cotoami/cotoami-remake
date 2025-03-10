package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Cotonoma, Node, UiState}
import cotoami.components.{materialSymbol, optionalClasses, toolButton}

object AppHeader {

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    header(
      data - "tauri-drag-region" := "default",
      data - "os" := model.systemInfo.map(_.os).getOrElse("")
    )(
      div(
        className := "header-content",
        data - "tauri-drag-region" := "default"
      )(
        model.repo.currentFocus.map(sectionCurrentFocus(_)).getOrElse(
          button(
            className := "app-info default",
            title := "View app info"
          )(
            img(
              className := "app-icon",
              alt := "Cotoami",
              src := "/images/logo/logomark.svg"
            )
          )
        ),
        section(className := "tools")(
          model.uiState.map(divToolButtons),
          divSearch(model.search),
          model.repo.nodes.operating.map(buttonNodeProfile)
        )
      )
    )

  private def sectionCurrentFocus(
      focus: (Node, Option[Cotonoma])
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val (node, cotonoma) = focus
    section(
      className := optionalClasses(
        Seq(
          ("current-focus", true),
          ("cotonoma-focused", cotonoma.isDefined)
        )
      ),
      data - "tauri-drag-region" := "default"
    )(
      a(
        className := "node-home",
        title := node.name,
        onClick := ((e) => {
          e.preventDefault()
          dispatch(AppMsg.FocusNode(node.id))
        })
      )(
        PartsNode.imgNode(node),
        span(className := "node-name")(node.name)
      ),
      cotonoma.map(cotonoma =>
        Fragment(
          materialSymbol("chevron_right", "arrow"),
          h1(className := "current-cotonoma")(cotonoma.name)
        )
      ),
      Option.when(context.repo.geolocationInFocus.isDefined)(
        button(
          className := "geolocation default",
          onClick := (e => {
            e.stopPropagation()
            dispatch(AppMsg.DisplayGeolocationInFocus)
          })
        )(materialSymbol("location_on"))
      )
    )
  }

  private def divSearch(
      search: PaneSearch.Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    import PaneSearch.Msg._
    div(className := "search")(
      input(
        `type` := "search",
        name := "query",
        value := search.queryInput,
        onChange := (e => dispatch(QueryInput(e.target.value))),
        onCompositionStart := (_ => dispatch(ImeCompositionStart)),
        onCompositionEnd := (_ => dispatch(ImeCompositionEnd))
      ),
      Option.when(!search.queryInput.isBlank) {
        button(
          className := "clear default",
          onClick := (_ => dispatch(ClearQuery))
        )(materialSymbol("close"))
      }
    )
  }

  private def divToolButtons(
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "tool-buttons")(
      Option.when(!context.repo.cotos.selectedIds.isEmpty) {
        button(
          className := "selected-cotos default tool",
          data - "tooltip" := "Selected cotos",
          data - "placement" := "bottom",
          onClick := (_ => dispatch(Modal.Msg.OpenModal(Modal.Selection())))
        )(
          materialSymbol("check_box"),
          span(className := "count")(context.repo.cotos.selectedIds.size)
        )
      },
      toolButton(
        classes = optionalClasses(
          Seq(
            ("toggle-geomap", true),
            ("opened", uiState.geomapOpened)
          )
        ),
        symbol = "public",
        tip = Some(if (uiState.geomapOpened) "Close map" else "Open map"),
        onClick = (_ => {
          if (uiState.geomapOpened)
            dispatch(AppMsg.CloseMap)
          else
            dispatch(AppMsg.OpenGeomap)
        })
      ),
      toolButton(
        classes = "toggle-dark-mode",
        symbol = if (uiState.isDarkMode) "light_mode" else "dark_mode",
        tip = Some(if (uiState.isDarkMode) "Light mode" else "Dark mode"),
        onClick = (_ => {
          val theme =
            if (uiState.isDarkMode) UiState.LightMode
            else UiState.DarkMode
          dispatch(AppMsg.SetTheme(theme))
        })
      )
    )

  private def buttonNodeProfile(
      node: Node
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    button(
      className := "node-profile, default",
      title := "Node profile",
      onClick := (_ =>
        dispatch(
          (Modal.Msg.OpenModal.apply _).tupled(
            Modal.NodeProfile(node.id, context.repo.nodes)
          )
        )
      )
    )(
      PartsNode.imgNode(node)
    )
}
