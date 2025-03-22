package cotoami.subparts

import scala.util.chaining._
import com.softwaremill.quicklens._

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Geolocation, UiState}
import cotoami.updates
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  ScrollArea,
  SplitPane
}
import cotoami.subparts.SectionGeomap

object PaneStock {

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.PaneStockMsg(this)
  }

  object Msg {
    case object OpenGeomap extends Msg
    case object CloseMap extends Msg
    case class SetMapOrientation(vertical: Boolean) extends Msg
    case class FocusGeolocation(location: Geolocation) extends Msg
    case object DisplayGeolocationInFocus extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.OpenGeomap =>
        updates.uiState(_.openGeomap, model)

      case Msg.CloseMap =>
        updates.uiState(_.closeMap, model)

      case Msg.SetMapOrientation(vertical) =>
        updates.uiState(_.setMapOrientation(vertical), model)
          .pipe { case (model, cmd) =>
            (model.modify(_.geomap).using(_.recreateMap), cmd)
          }

      case Msg.FocusGeolocation(location) =>
        updates.uiState(_.openGeomap, model)
          .pipe { case (model, cmd) =>
            (model.modify(_.geomap).using(_.focus(location)), cmd)
          }

      case Msg.DisplayGeolocationInFocus =>
        model.repo.geolocationInFocus
          .map(location =>
            updates.uiState(_.openGeomap, model)
              .pipe { case (model, cmd) =>
                (model.modify(_.geomap).using(_.moveTo(location)), cmd)
              }
          )
          .getOrElse((model, Cmd.none))
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  final val PaneName = "PaneStock"

  final val PaneMapName = "PaneMap"
  final val PaneMapDefaultWidth = 400

  def apply(
      model: Model,
      uiState: UiState
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(className := "stock fill")(
      if (uiState.mapOpened)
        SplitPane(
          vertical = uiState.mapVertical,
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
            sectionCotoGraph(model, uiState)(model, dispatch)
          )
        )
      else
        sectionCotoGraph(model, uiState)(model, dispatch)
    )

  private def divMap(model: Model, uiState: UiState)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "map fill")(
      Option.when(uiState.geomapOpened) {
        SectionGeomap(model.geomap)(model, dispatch)
      },
      toolButton(
        classes = "change-split-orientation",
        symbol =
          if (uiState.mapVertical)
            "splitscreen_top"
          else
            "splitscreen_left",
        tip =
          if (uiState.mapVertical)
            Some("Switch to Top Pane")
          else
            Some("Switch to Left Pane"),
        onClick = _ => dispatch(Msg.SetMapOrientation(!uiState.mapVertical))
      ),
      button(
        className := "default close-map",
        onClick := (_ => dispatch(Msg.CloseMap))
      )(
        materialSymbol("arrow_drop_up")
      )
    )

  final val CotoGraphScrollableElementId = "scrollable-coto-graph"

  private def sectionCotoGraph(
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
          ("coto-graph", true),
          ("fill", true),
          ("with-traversals-opened", sectionTraversals.isDefined)
        )
      )
    )(
      if (sectionTraversals.isDefined)
        ScrollArea(
          scrollableClassName = Some("scrollable-coto-graph"),
          scrollableElementId = Some(CotoGraphScrollableElementId)
        )(contents)
      else
        contents
    )
  }
}
