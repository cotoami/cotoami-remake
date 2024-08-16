package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.backend.{Cotonoma, Node}
import cotoami.components.{materialSymbol, optionalClasses, toolButton}

object AppHeader {

  def apply(
      model: Model
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    header(
      data - "tauri-drag-region" := "default",
      data - "os" := model.systemInfo.map(_.os).getOrElse("")
    )(
      div(
        className := "header-content",
        data - "tauri-drag-region" := "default"
      )(
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
        model.domain.location.map(sectionLocation),
        section(className := "tools")(
          model.uiState.map(divToolButtons),
          divSearch,
          model.domain.nodes.operating.map(buttonNodeProfile)
        )
      )
    )

  private def sectionLocation(
      location: (Node, Option[Cotonoma])
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
    val (node, cotonoma) = location
    section(
      className := "location",
      data - "tauri-drag-region" := "default"
    )(
      a(
        className := "node-home",
        title := node.name,
        onClick := ((e) => {
          e.preventDefault()
          dispatch(AppMsg.FocusNode(node.id))
        })
      )(imgNode(node)),
      cotonoma.map(cotonoma =>
        Fragment(
          materialSymbol("chevron_right", "arrow"),
          h1(className := "current-cotonoma")(cotonoma.name)
        )
      )
    )
  }

  private def divSearch: ReactElement =
    div(className := "search")(
      input(
        `type` := "search",
        name := "query"
      )
    )

  private def divToolButtons(
      uiState: UiState
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    div(className := "tool-buttons")(
      toolButton(
        symbol = "public",
        tip = if (uiState.geomapOpened) "Close map" else "Open map",
        classes = optionalClasses(
          Seq(
            ("toggle-geomap", true),
            ("opened", uiState.geomapOpened)
          )
        ),
        onClick = (_ => {
          if (uiState.geomapOpened)
            dispatch(AppMsg.CloseMap)
          else
            dispatch(AppMsg.OpenGeomap)
        })
      )
    )

  private def buttonNodeProfile(
      node: Node
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    button(
      className := "node-profile, default",
      title := "Node profile",
      onClick := (_ =>
        dispatch(
          (Modal.Msg.OpenModal.apply _).tupled(Modal.NodeProfile(node)).toApp
        )
      )
    )(
      imgNode(node)
    )
}
