package cotoami.models

import scala.collection.immutable.HashSet
import org.scalajs.dom
import com.softwaremill.quicklens._
import cats.effect.IO

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import fui.Cmd
import cotoami.Msg
import cotoami.utils.Log
import cotoami.models.Cotonoma
import cotoami.subparts.{PaneFlow, PaneStock}

case class UiState(
    theme: String = UiState.DefaultTheme,
    paneToggles: Map[String, Boolean] = Map(
      PaneStock.PaneName -> false // fold PaneStock by default
    ),
    paneSizes: Map[String, Int] = Map(),
    reverseMainPanes: Boolean = false,
    pinsInColumns: HashSet[String] = HashSet.empty,
    mapVertical: Boolean = false,
    geomapOpened: Boolean = false
) {
  def isDarkMode: Boolean = theme == UiState.DarkMode

  def paneOpened(name: String): Boolean =
    paneToggles.getOrElse(name, true) // open by default

  def setPaneOpen(name: String, open: Boolean): UiState = {
    val toggles = paneToggles + (name -> open)
    (toggles.get(PaneFlow.PaneName), toggles.get(PaneStock.PaneName)) match {
      // Not allow fold both PaneFlow and PaneStock at the same time.
      case (Some(false), Some(false)) => this
      case _                          => copy(paneToggles = toggles)
    }
  }

  def resizePane(name: String, newSize: Int): UiState =
    copy(paneSizes = paneSizes + (name -> newSize))

  def swapPane: UiState = copy(reverseMainPanes = !reverseMainPanes)

  def setPinsInColumns(cotonoma: Id[Cotonoma], inColumns: Boolean): UiState =
    this.modify(_.pinsInColumns).using(
      if (inColumns)
        _ + cotonoma.uuid
      else
        _ - cotonoma.uuid
    )

  def arePinsInColumns(cotonoma: Id[Cotonoma]): Boolean =
    pinsInColumns.contains(cotonoma.uuid)

  def setMapOrientation(vertical: Boolean): UiState =
    copy(mapVertical = vertical)

  def openGeomap: UiState = copy(geomapOpened = true)

  def closeMap: UiState = copy(geomapOpened = false)

  def mapOpened: Boolean = geomapOpened

  def save: Cmd.One[Msg] =
    Cmd(IO {
      dom.window.localStorage
        .setItem(UiState.StorageKey, this.asJson.toString())
      None
    })
}

object UiState {
  final val StorageKey = "UiState"
  final val DarkMode = "dark"
  final val LightMode = "light"
  final val DefaultTheme = DarkMode

  implicit val encoder: Encoder[UiState] = deriveEncoder
  implicit val decoder: Decoder[UiState] = deriveDecoder

  def restore(createMsg: Option[UiState] => Msg): Cmd.One[Msg] =
    Cmd(IO {
      val value = dom.window.localStorage.getItem(StorageKey)
      val msg = if (value != null) {
        decode[UiState](value) match {
          case Right(uiState) => createMsg(Some(uiState))
          case Left(error) => {
            dom.window.localStorage.removeItem(StorageKey)
            Msg.AddLogEntry(
              Log.Error,
              "Invalid uiState in localStorage.",
              Some(value)
            )
          }
        }
      } else {
        createMsg(None)
      }
      Some(msg)
    })
}
