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
import java.time._
import java.time.format.DateTimeFormatter

import fui.FunctionalUI.Cmd
import cotoami.utils.Log
import cotoami.backend._
import cotoami.repositories._
import cotoami.subparts._

case class Model(
    url: URL,
    log: Log = Log(),
    context: Model.Context = Model.Context(),
    logViewToggle: Boolean = false,
    systemInfo: Option[SystemInfoJson] = None,

    // uiState that can be saved in localStorage separately from app data.
    // It will be `None` before being restored from localStorage on init.
    uiState: Option[Model.UiState] = None,
    contentTogglesOpened: Set[String] = Set.empty,

    // Database
    databaseFolder: Option[String] = None,
    lastChangeNumber: Double = 0,

    // Domain
    domain: Domain = Domain(),

    // subparts
    flowInput: FormCoto.Model,
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
}

object Model {
  val DefaultDateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  val SameYearFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

  case class Context(
      zone: ZoneId = ZoneId.of("UTC")
  ) {
    def toDateTime(instant: Instant): LocalDateTime =
      LocalDateTime.ofInstant(instant, this.zone)

    def formatDateTime(instant: Instant): String = {
      this.toDateTime(instant).format(DefaultDateTimeFormatter)
    }

    def display(instant: Instant): String = {
      val now = LocalDateTime.now(this.zone)
      val dateTime = this.toDateTime(instant)
      if (dateTime.toLocalDate() == now.toLocalDate()) {
        dateTime.format(DateTimeFormatter.ISO_LOCAL_TIME)
      } else if (dateTime.getYear() == now.getYear()) {
        dateTime.format(SameYearFormatter)
      } else {
        dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
      }
    }
  }

  case class UiState(
      paneToggles: Map[String, Boolean] = Map(
        PaneStock.PaneName -> false // fold PaneStock by default
      ),
      paneSizes: Map[String, Int] = Map(),
      pinnedInColumns: HashSet[String] = HashSet.empty
  ) {
    def paneOpened(name: String): Boolean =
      this.paneToggles.getOrElse(name, true) // open by default

    def togglePane(name: String): UiState = {
      val toggles = this.paneToggles + (name -> !this.paneOpened(name))
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
