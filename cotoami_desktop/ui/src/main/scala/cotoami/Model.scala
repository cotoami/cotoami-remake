package cotoami

import org.scalajs.dom

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import cats.effect.IO

import fui.FunctionalUI.Cmd
import cotoami.backend.{SystemInfo, Node}

case class Model(
    log: Log = Log(),
    logViewToggle: Boolean = false,
    systemInfo: Option[SystemInfo] = None,
    testDir: Option[String] = None,

    // UI state that can be saved in localStorage separately from app data.
    // It will be `None` before being restored from localStorage on init.
    uiState: Option[Model.UiState] = None,

    // Node
    currentNode: Option[Node] = None,
    localNode: Option[Node] = None
)

object Model {

  case class UiState(
      paneToggles: Map[String, Boolean] = Map(),
      paneSizes: Map[String, Int] = Map()
  ) {
    def paneOpened(name: String): Boolean =
      this.paneToggles.getOrElse(name, true)

    def togglePane(name: String): UiState =
      this.copy(paneToggles =
        this.paneToggles + (name -> !this.paneOpened(name))
      )

    def resizePane(name: String, newSize: Int): UiState =
      this.copy(paneSizes = this.paneSizes + (name -> newSize))

    def save(): Cmd[Msg] =
      IO {
        dom.window.localStorage
          .setItem(UiState.StorageKey, this.asJson.toString())
        None
      }
  }

  object UiState {
    val StorageKey = "uiState"

    implicit val encoder: Encoder[UiState] = deriveEncoder
    implicit val decoder: Decoder[UiState] = deriveDecoder

    def restore(createMsg: Option[UiState] => Msg): Cmd[Msg] =
      IO {
        val value = dom.window.localStorage.getItem(StorageKey)
        val msg = if (value != null) {
          decode[UiState](value) match {
            case Right(uiState) => createMsg(Some(uiState))
            case Left(error) => {
              dom.window.localStorage.removeItem(StorageKey)
              cotoami.AddLogEntry(
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
      }
  }
}
