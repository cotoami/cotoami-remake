package cotoami

import scala.collection.immutable.HashSet
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.URL

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import cats.effect.IO
import com.softwaremill.quicklens._

import fui.FunctionalUI.Cmd
import cotoami.utils.Log
import cotoami.backend._
import cotoami.repositories._
import cotoami.subparts._

case class Model(
    url: URL,
    log: Log = Log(),
    context: Context = Context(),
    logViewToggle: Boolean = false,
    systemInfo: Option[SystemInfoJson] = None,
    databaseFolder: Option[String] = None,

    // uiState that can be saved in localStorage separately from app data.
    // It will be `None` before being restored from localStorage on init.
    uiState: Option[Model.UiState] = None,

    // This value will be updated by and referred to from subparts that need to
    // control text input according to IME state.
    imeActive: Boolean = false,

    // Domain aggregate root
    domain: Domain = Domain(),

    // subparts
    flowInput: FormCoto.Model,
    traversals: SectionTraversals.Model = SectionTraversals.Model(),
    modalWelcome: ModalWelcome.Model = ModalWelcome.Model()
) {
  def path: String = this.url.pathname + this.url.search + this.url.hash

  def debug(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.debug(message, details))
  def info(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.info(message, details))
  def warn(message: String, details: Option[String] = None): Model =
    this.copy(log = this.log.warn(message, details))
  def error(message: String, error: Option[ErrorJson]): Model =
    this.copy(log = this.log.error(message, error.map(js.JSON.stringify(_))))

  def clearTraversals: Model =
    this.copy(traversals = SectionTraversals.Model())
}

object Model {

  case class UiState(
      paneToggles: Map[String, Boolean] = Map(
        PaneStock.PaneName -> false // fold PaneStock by default
      ),
      paneSizes: Map[String, Int] = Map(),
      pinnedInColumns: HashSet[String] = HashSet.empty
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
