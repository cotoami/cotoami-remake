package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.{Context, Model, Msg => AppMsg}
import cotoami.models.UiState
import cotoami.components.{optionalClasses, MapLibre, ScrollArea, SplitPane}

object PaneStock {
  final val PaneName = "PaneStock"

  final val PaneMapName = "PaneMap"
  final val PaneMapDefaultWidth = 400

  final val ScrollableElementId = "scrollable-stock-with-traversals"

  def apply(
      model: Model,
      uiState: UiState
  )(implicit dispatch: AppMsg => Unit): ReactElement = {
    section(className := "stock")(
      SplitPane(
        vertical = false,
        initialPrimarySize = uiState.paneSizes.getOrElse(
          PaneMapName,
          PaneMapDefaultWidth
        ),
        onPrimarySizeChanged =
          Some((newSize) => dispatch(AppMsg.ResizePane(PaneMapName, newSize))),
        primary = SplitPane.Primary.Props()(
          div(className := "map")(
            MapLibre(id = "main-geomap", defaultPosition = (139.5, 35.7))
          )
        ),
        secondary = SplitPane.Secondary.Props()(
          sectionLinkedCotos(model, uiState)(model, dispatch)
        )
      )
    )
  }

  def sectionLinkedCotos(
      model: Model,
      uiState: UiState
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
    val sectionTraversals = SectionTraversals(model.traversals)
    val contents = Fragment(
      SectionPinnedCotos(model, uiState),
      sectionTraversals
    )

    section(
      className := optionalClasses(
        Seq(
          ("linked-cotos", true),
          ("with-traversals-opened", sectionTraversals.isDefined)
        )
      )
    )(
      if (sectionTraversals.isDefined)
        ScrollArea(scrollableElementId = Some(ScrollableElementId))(contents)
      else
        contents
    )
  }
}
