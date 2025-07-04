package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.{materialSymbol, toolButton}

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Cotonoma, Node, UiState}
import cotoami.repository.Root
import cotoami.subparts.PaneStock

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
        model.repo.currentFocus.map(sectionCurrentFocus),
        section(className := "tools")(
          model.uiState.map(divToolButtons),
          divSearch(model.search),
          model.repo.nodes.self.map(buttonNodeProfile)
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
      cotonoma.map(fragmentCurrentCotonoma),
      Option.when(context.repo.geolocationInFocus.isDefined)(
        toolButton(
          classes = "geolocation",
          symbol = "location_on",
          onClick = e => dispatch(PaneStock.Msg.DisplayGeolocationInFocus)
        )
      )
    )
  }

  private def fragmentCurrentCotonoma(
      cotonoma: Cotonoma
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    Fragment(
      materialSymbol("chevron_right", "arrow"),
      h1(className := "current-cotonoma")(cotonoma.name),
      context.repo.cotonomas.totalPostsInFocus.map(posts =>
        Fragment(
          span(className := "total-posts")(
            s"(${context.i18n.format(posts.max(0))})"
          ),
          Option.when(posts <= 0 && context.repo.canDeleteEmpty(cotonoma)) {
            buttonDeleteCotonoma(cotonoma)
          }
        )
      )
    )

  private def buttonDeleteCotonoma(cotonoma: Cotonoma)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    toolButton(
      classes = "delete-cotonoma",
      symbol = "delete",
      tip = Some(context.i18n.text.DeleteCotonoma),
      onClick = e =>
        dispatch(
          Modal.Msg.OpenModal(
            Modal.Confirm(
              context.i18n.text.ConfirmDeleteCotonoma,
              Root.Msg.DeleteCotonoma(cotonoma)
            )
          )
        )
    )

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
          data - "tooltip" := context.i18n.text.ModalSelection_title,
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
        tip = Some(
          if (uiState.geomapOpened) context.i18n.text.CloseMap
          else context.i18n.text.OpenMap
        ),
        onClick = (_ => {
          if (uiState.geomapOpened)
            dispatch(PaneStock.Msg.CloseMap)
          else
            dispatch(PaneStock.Msg.OpenGeomap)
        })
      ),
      toolButton(
        classes = "swap-pane",
        symbol = "swap_horiz",
        tip = Some(context.i18n.text.SwapPane),
        onClick = _ => dispatch(AppMsg.SwapPane)
      ),
      toolButton(
        classes = "toggle-dark-mode",
        symbol = if (uiState.isDarkMode) "light_mode" else "dark_mode",
        tip = Some(
          if (uiState.isDarkMode) context.i18n.text.LightMode
          else context.i18n.text.DarkMode
        ),
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
      title := context.i18n.text.ModalNodeProfile_title,
      onClick := (_ =>
        dispatch(Modal.Msg.OpenModal(Modal.NodeProfile(node.id)))
      )
    )(
      PartsNode.imgNode(node)
    )
}
