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
    pinnedInColumns: HashSet[String] = HashSet.empty,
    geomapOpened: Boolean = false
) {
  def paneOpened(name: String): Boolean =
    this.paneToggles.getOrElse(name, true) // open by default

  def openOrClosePane(name: String, open: Boolean): UiState = {
    val toggles = this.paneToggles + (name -> open)
    (toggles.get(PaneFlow.PaneName), toggles.get(PaneStock.PaneName)) match {
      // Not allow fold both PaneFlow and PaneStock at the same time.
      case (Some(false), Some(false)) => this
      case _                          => this.copy(paneToggles = toggles)
    }
  }

  def resizePane(name: String, newSize: Int): UiState =
    this.copy(paneSizes = this.paneSizes + (name -> newSize))

  def setPinnedInColumns(
      cotonoma: Id[Cotonoma],
      pinnedInColumns: Boolean
  ): UiState =
    this.modify(_.pinnedInColumns).using(
      if (pinnedInColumns)
        _ + cotonoma.uuid
      else
        _ - cotonoma.uuid
    )

  def isPinnedInColumns(cotonoma: Id[Cotonoma]): Boolean =
    this.pinnedInColumns.contains(cotonoma.uuid)

  def openGeomap: UiState =
    this.copy(geomapOpened = true).openOrClosePane(PaneStock.PaneName, true)

  def closeMap: UiState =
    this.copy(geomapOpened = false)

  def mapOpened: Boolean = this.geomapOpened

  def save: Cmd[Msg] =
    Cmd(IO {
      dom.window.localStorage
        .setItem(UiState.StorageKey, this.asJson.toString())
      None
    })
}

object UiState {
  final val StorageKey = "UiState"
  final val DefaultTheme = "dark"

  implicit val encoder: Encoder[UiState] = deriveEncoder
  implicit val decoder: Decoder[UiState] = deriveDecoder

  def restore(createMsg: Option[UiState] => Msg): Cmd[Msg] =
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
