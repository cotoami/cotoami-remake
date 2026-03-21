package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.core.facade.Hooks._
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.components.{materialSymbol, toolButton}

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Cotonoma, Node, UiState}
import cotoami.repository.Root
import cotoami.subparts.modeless.ModelessGeomap
import cotoami.subparts.modeless.ModelessNodeProfile

object AppHeader {

  def apply(
      model: Model
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
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
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
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
          onClick = _ => dispatch(PaneStock.Msg.DisplayGeolocationInFocus)
        )
      )
    )
  }

  private def fragmentCurrentCotonoma(
      cotonoma: Cotonoma
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
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

  private def buttonDeleteCotonoma(cotonoma: Cotonoma)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    toolButton(
      classes = "delete-cotonoma",
      symbol = "delete",
      tip = Some(context.i18n.text.DeleteCotonoma),
      onClick = _ =>
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
  )(using dispatch: Into[AppMsg] => Unit): ReactElement = {
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
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
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
      buttonGeomap(uiState),
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

  private def buttonGeomap(uiState: UiState)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    GeomapButton.component(
      GeomapButton.Props(
        uiState = uiState,
        context = context,
        dispatch = dispatch
      )
    )

  private def buttonNodeProfile(
      node: Node
  )(using context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    button(
      className := "node-profile default",
      title := context.i18n.text.ModalNodeProfile_title,
      onClick := (_ => dispatch(ModelessNodeProfile.Msg.Open(node.id)))
    )(
      PartsNode.imgNode(node)
    )

  private object GeomapButton {
    case class Props(
        uiState: UiState,
        context: Context,
        dispatch: Into[AppMsg] => Unit
    )

    val component = FunctionalComponent[Props] { props =>
      val (hovered, setHovered) = useState(false)
      val (shiftPressed, setShiftPressed) = useState(false)

      val canOpenModelessGeomap = props.context.modeless.geomap.isEmpty
      val showWindowTip =
        !props.uiState.geomapOpened &&
          canOpenModelessGeomap &&
          hovered &&
          shiftPressed

      val geomapTip =
        if (props.uiState.geomapOpened) props.context.i18n.text.CloseMap
        else if (showWindowTip)
          s"${props.context.i18n.text.OpenMap} (${props.context.i18n.text.Window})"
        else props.context.i18n.text.OpenMap

      val handleKeyDown: js.Function1[dom.KeyboardEvent, Unit] =
        (e: dom.KeyboardEvent) => setShiftPressed(_ => e.shiftKey)
      val handleKeyUp: js.Function1[dom.KeyboardEvent, Unit] =
        (e: dom.KeyboardEvent) => setShiftPressed(_ => e.shiftKey)

      useEffect(
        () => {
          if (hovered) {
            dom.window.addEventListener("keydown", handleKeyDown)
            dom.window.addEventListener("keyup", handleKeyUp)
          }

          () => {
            dom.window.removeEventListener("keydown", handleKeyDown)
            dom.window.removeEventListener("keyup", handleKeyUp)
          }
        },
        Seq(hovered)
      )

      button(
        className := s"default tool ${optionalClasses(Seq(("toggle-geomap", true), ("opened", props.uiState.geomapOpened)))}",
        data - "tooltip" := geomapTip,
        data - "placement" := "bottom",
        onClick := (e => {
          if (props.uiState.geomapOpened)
            props.dispatch(PaneStock.Msg.CloseMap)
          else if (e.shiftKey && canOpenModelessGeomap)
            props.dispatch(ModelessGeomap.Msg.Open)
          else
            props.dispatch(PaneStock.Msg.OpenGeomap)
        }),
        onMouseEnter := (e => {
          setHovered(_ => true)
          setShiftPressed(_ => e.shiftKey)
        }),
        onMouseMove := (e => setShiftPressed(_ => e.shiftKey)),
        onMouseLeave := (_ => {
          setHovered(_ => false)
          setShiftPressed(_ => false)
        }),
        onMouseOver := (_.stopPropagation())
      )(
        materialSymbol("public")
      )
    }
  }
}
