package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  ScrollArea,
  SplitPane
}

object PaneStock {
  final val PaneName = "PaneStock"

  final val PaneMapName = "PaneMap"
  final val PaneMapDefaultWidth = 400

  final val ScrollableElementId = "scrollable-stock-with-traversals"

  def apply(
      model: Model,
      uiState: UiState
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "stock fill")(
      if (uiState.mapOpened)
        SplitPane(
          vertical = false,
          initialPrimarySize = uiState.paneSizes.getOrElse(
            PaneMapName,
            PaneMapDefaultWidth
          ),
          onPrimarySizeChanged = Some((newSize) =>
            dispatch(AppMsg.ResizePane(PaneMapName, newSize))
          ),
          primary = SplitPane.Primary.Props()(
            divMap(model, uiState)
          ),
          secondary = SplitPane.Secondary.Props()(
            sectionLinks(model, uiState)(model, dispatch)
          )
        )
      else
        sectionLinks(model, uiState)(model, dispatch)
    )

  private def divMap(model: Model, uiState: UiState)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "map fill")(
      Option.when(uiState.geomapOpened) {
        SectionGeomap(model.geomap)(model, dispatch)
      },
      div(className := "close-map-button")(
        button(
          className := "default close-map",
          onClick := (_ => dispatch(AppMsg.CloseMap))
        )(
          materialSymbol("arrow_drop_up")
        )
      )
    )

  private def sectionLinks(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val sectionTraversals = SectionTraversals(model.traversals)
    val contents = Fragment(
      SectionPins(uiState),
      sectionTraversals
    )
    section(
      className := optionalClasses(
        Seq(
          ("links", true),
          ("fill", true),
          ("with-traversals-opened", sectionTraversals.isDefined)
        )
      )
    )(
      if (sectionTraversals.isDefined)
        ScrollArea(
          className = Some("pins-and-traversals"),
          scrollableClassName = Some("scrollable-pins-and-traversals"),
          scrollableElementId = Some(ScrollableElementId)
        )(contents)
      else
        contents
    )
  }
}
