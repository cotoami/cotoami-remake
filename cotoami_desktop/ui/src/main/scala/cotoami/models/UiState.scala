package cotoami.models

import scala.collection.immutable.HashSet
import org.scalajs.dom
import com.softwaremill.quicklens._
import cats.effect.IO

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import marubinotto.fui.Cmd
import cotoami.Msg
import cotoami.models.Cotonoma
import cotoami.subparts.PaneStock

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

  def setPaneOpen(name: String, open: Boolean): UiState =
    this.modify(_.paneToggles).using(_ + (name -> open))

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

  def restore: Cmd.One[Option[UiState]] =
    Cmd(IO {
      val value = dom.window.localStorage.getItem(StorageKey)
      if (value != null) {
        decode[UiState](value) match {
          case Right(uiState) => Some(Some(uiState))
          case Left(error) => {
            println(s"Invalid UI state in localStorage: ${error.toString()}")
            dom.window.localStorage.removeItem(StorageKey)
            Some(None)
          }
        }
      } else {
        Some(None)
      }
    })
}
