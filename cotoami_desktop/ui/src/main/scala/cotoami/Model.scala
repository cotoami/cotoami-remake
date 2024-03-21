package cotoami

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.URL

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import com.softwaremill.quicklens._
import cats.effect.IO

import fui.FunctionalUI.Cmd
import cotoami.Id
import cotoami.backend.{Cotonoma, Error, Node, SystemInfo}
import cotoami.subparts.ModalWelcome

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

    // Node
    nodes: Map[Id[Node], Node] = Map.empty,
    localNodeId: Option[Id[Node]] = None,
    operatingNodeId: Option[Id[Node]] = None,
    selectedNodeId: Option[Id[Node]] = None,
    parentNodeIds: Seq[Id[Node]] = Seq.empty,

    // Cotonoma
    cotonomas: Map[Id[Cotonoma], Cotonoma] = Map.empty,
    selectedCotonomaId: Option[Id[Cotonoma]] = None,
    superCotonomaIds: Seq[Id[Cotonoma]] = Seq.empty,
    subCotonomaIds: Seq[Id[Cotonoma]] = Seq.empty,
    recentCotonomaIds: Seq[Id[Cotonoma]] = Seq.empty,

    // WelcomeModal
    modalWelcome: ModalWelcome.Model = ModalWelcome.Model()
) {
  def error(error: Error, message: String): Model =
    this.copy(log = this.log.error(message, Some(js.JSON.stringify(error))))

  def path(): String = this.url.pathname + this.url.search + this.url.hash

  def clearSelection(): Model =
    this.copy(
      selectedNodeId = None,
      selectedCotonomaId = None,
      superCotonomaIds = Seq.empty,
      subCotonomaIds = Seq.empty
    )

  //
  // Node
  //

  def node(id: Id[Node]): Option[Node] = this.nodes.get(id)

  def isSelectingNode(node: Node): Boolean =
    this.selectedNodeId.map(_ == node.id()).getOrElse(false)

  def localNode(): Option[Node] = this.localNodeId.flatMap(node(_))

  def operatingNode(): Option[Node] = this.operatingNodeId.flatMap(node(_))

  def selectedNode(): Option[Node] = this.selectedNodeId.flatMap(node(_))

  def currentNode(): Option[Node] = selectedNode().orElse(this.operatingNode())

  //
  // Cotonoma
  //

  def cotonoma(id: Id[Cotonoma]): Option[Cotonoma] = this.cotonomas.get(id)

  def rootCotonomaId(): Option[Id[Cotonoma]] =
    currentNode().flatMap(node => Option(node.rootCotonomaId()))

  def isSelectingCotonoma(cotonoma: Cotonoma): Boolean =
    this.selectedCotonomaId.map(_ == cotonoma.id()).getOrElse(false)

  def selectedCotonoma(): Option[Cotonoma] =
    this.selectedCotonomaId.flatMap(cotonoma(_))

  def superCotonomas(): Seq[Cotonoma] =
    this.superCotonomaIds.map(this.cotonoma(_)).flatten

  def subCotonomas(): Seq[Cotonoma] =
    this.subCotonomaIds.map(this.cotonoma(_)).flatten

  def currentCotonomaId(): Option[Id[Cotonoma]] =
    this.selectedCotonomaId.orElse(currentNode().map(_.rootCotonomaId()))

  def recentCotonomas(): Seq[Cotonoma] = {
    val rootCotonomaId = this.rootCotonomaId()
    this.recentCotonomaIds.filter(Some(_) != rootCotonomaId).map(
      this.cotonoma(_)
    ).flatten
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

    def save(): Cmd[Msg] =
      Cmd(IO {
        dom.window.localStorage
          .setItem(UiState.StorageKey, this.asJson.toString())
        None
      })
  }

  object UiState {
    val StorageKey = "uiState"

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
