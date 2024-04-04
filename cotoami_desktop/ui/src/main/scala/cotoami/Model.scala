package cotoami

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.URL

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import cats.effect.IO

import fui.FunctionalUI.Cmd
import cotoami.backend.{Cotonoma, Cotonomas, Cotos, Error, Nodes, SystemInfo}
import cotoami.subparts.{FormCoto, ModalWelcome}

case class Model(
    url: URL,
    log: Log = Log(),
    logViewToggle: Boolean = false,
    systemInfo: Option[SystemInfo] = None,

    // UI state that can be saved in localStorage separately from app data.
    // It will be `None` before being restored from localStorage on init.
    uiState: Option[Model.UiState] = None,

    // Database
    databaseFolder: Option[String] = None,
    lastChangeNumber: Double = 0,

    // Entities
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),

    // subparts
    flowInput: FormCoto.Model,
    modalWelcome: ModalWelcome.Model = ModalWelcome.Model()
) {
  def error(error: Error, message: String): Model =
    this.copy(log = this.log.error(message, Some(js.JSON.stringify(error))))

  def path: String = this.url.pathname + this.url.search + this.url.hash

  def clearSelection(): Model =
    this.copy(
      nodes = this.nodes.deselect(),
      cotonomas = Cotonomas(),
      cotos = Cotos()
    )

  def rootCotonomaId: Option[Id[Cotonoma]] =
    this.nodes.current.flatMap(node => Option(node.rootCotonomaId))

  def currentCotonoma: Option[Cotonoma] =
    this.cotonomas.selectedId.orElse(
      this.nodes.current.map(_.rootCotonomaId)
    ).flatMap(this.cotonomas.get)

  lazy val recentCotonomasWithoutRoot: Seq[Cotonoma] = {
    val rootId = this.rootCotonomaId
    this.cotonomas.recent.filter(c => Some(c.id) != rootId)
  }

  lazy val superCotonomasWithoutRoot: Seq[Cotonoma] = {
    val rootId = this.rootCotonomaId
    this.cotonomas.supers.filter(c => Some(c.id) != rootId)
  }
}

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

    def save: Cmd[Msg] =
      Cmd(IO {
        dom.window.localStorage
          .setItem(UiState.StorageKey, this.asJson.toString())
        None
      })
  }

  object UiState {
    val StorageKey = "UiState"

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
      })
  }
}
