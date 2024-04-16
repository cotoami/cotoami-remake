package cotoami

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.URL

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import cats.effect.IO
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

    // Repositories
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),

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

  def clearSelection(): Model =
    this.copy(
      nodes = this.nodes.deselect(),
      cotonomas = Cotonomas(),
      cotos = Cotos()
    )

  def rootCotonomaId: Option[Id[Cotonoma]] =
    this.nodes.current.flatMap(node => Option(node.rootCotonomaId))

  def isRoot(id: Id[Cotonoma]): Boolean = Some(id) == this.rootCotonomaId

  def currentCotonoma: Option[Cotonoma] =
    this.cotonomas.selectedId.orElse(
      this.nodes.current.map(_.rootCotonomaId)
    ).flatMap(this.cotonomas.get)

  def location: Option[(Node, Option[Cotonoma])] =
    this.nodes.current.map(currentNode =>
      // The location contains a cotonoma only when one is selected,
      // otherwise the root cotonoma of the current node will be implicitly
      // used as the current cotonoma.
      this.cotonomas.selected match {
        case Some(cotonoma) =>
          (
            this.nodes.get(cotonoma.nodeId).getOrElse(currentNode),
            Some(cotonoma)
          )
        case None => (currentNode, None)
      }
    )

  lazy val recentCotonomas: Seq[Cotonoma] = {
    val rootId = this.rootCotonomaId
    this.cotonomas.recent.filter(c => Some(c.id) != rootId)
  }

  lazy val superCotonomas: Seq[Cotonoma] = {
    val rootId = this.rootCotonomaId
    this.cotonomas.supers.filter(c => Some(c.id) != rootId)
  }

  lazy val timeline: Seq[Coto] =
    this.nodes.current match {
      case Some(node) =>
        this.cotos.timeline.filter(_.nameAsCotonoma != Some(node.name))
      case None => this.cotos.timeline
    }
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
      paneSizes: Map[String, Int] = Map()
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
