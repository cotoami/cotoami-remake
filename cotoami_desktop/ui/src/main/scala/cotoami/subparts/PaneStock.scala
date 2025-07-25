package cotoami.subparts

import scala.util.chaining._
import com.softwaremill.quicklens._
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.{
  materialSymbol,
  toolButton,
  ScrollArea,
  SplitPane
}

import cotoami.{Context, Into, Model, Msg => AppMsg}
import cotoami.models.{Geolocation, UiState}
import cotoami.updates
import cotoami.subparts.SectionGeomap

object PaneStock {

  final val PaneId = "stock-pane"
  final val PaneName = "PaneStock"
  final val DefaultWidth = 650

  final val PaneMapName = "PaneMap"
  final val PaneMapDefaultSize = 400

  def currentWidth: Double = dom.document.getElementById(PaneId) match {
    case element: HTMLElement => element.offsetWidth
    case _                    => 0
  }

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
        model
          .pipe(updates.uiState(_.openGeomap))
          .pipe(
            updates.addCmd((_: Model) =>
              Browser.send(AppMain.Msg.SetPaneStockOpen(true).into)
            )
          )

      case Msg.CloseMap =>
        model
          .modify(_.geomap).using(_.unfocus)
          .pipe(updates.uiState(_.closeMap))

      case Msg.SetMapOrientation(vertical) =>
        model.pipe(
          updates.uiState(
            _.setMapOrientation(vertical)
              .resizePane(PaneMapName, PaneMapDefaultSize)
          )
        )

      case Msg.FocusGeolocation(location) =>
        update(Msg.OpenGeomap, model)
          .pipe { case (model, cmd) =>
            (model.modify(_.geomap).using(_.focus(location)), cmd)
          }

      case Msg.DisplayGeolocationInFocus =>
        model.repo.geolocationInFocus
          .map(location =>
            model
              .pipe(update(Msg.OpenGeomap, _))
              .pipe { case (model, cmd) =>
                (model.modify(_.geomap).using(_.moveTo(location)), cmd)
              }
          )
          .getOrElse((model, Cmd.none))
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model,
      uiState: UiState
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    section(id := PaneId, className := "stock fill")(
      if (uiState.mapOpened)
        SplitPane(
          vertical = uiState.mapVertical,
          initialPrimarySize = uiState.paneSizes.getOrElse(
            PaneMapName,
            PaneMapDefaultSize
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
          // Re-create the component on orientation change
        ).withKey(uiState.mapVertical.toString())
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
