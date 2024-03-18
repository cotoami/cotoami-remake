package cotoami

import org.scalajs.dom
import org.scalajs.dom.URL

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import cats.effect.IO

import fui.FunctionalUI.Cmd
import cotoami.backend.{Cotonoma, Node, SystemInfo}
import cotoami.subparts.ModalWelcome

case class Model(
    url: URL,
    log: Log = Log(),
    logViewToggle: Boolean = false,
    systemInfo: Option[SystemInfo] = None,

    // UI state that can be saved in localStorage separately from app data.
    // It will be `None` before being restored from localStorage on init.
    uiState: Option[Model.UiState] = None,

    // Node
    nodes: Map[String, Node] = Map.empty,
    localNodeId: Option[String] = None,
    parentNodeIds: Seq[String] = Seq.empty,
    operatingNodeId: Option[String] = None,
    selectedNodeId: Option[String] = None,

    // Cotonoma
    cotonomas: Map[String, Cotonoma] = Map.empty,
    selectedCotonomaId: Option[String] = None,
    superCotonomaIds: Seq[String] = Seq.empty,
    subCotonomaIds: Seq[String] = Seq.empty,
    recentCotonomaIds: Seq[String] = Seq.empty,

    // WelcomeModal
    modalWelcome: ModalWelcome.Model = ModalWelcome.Model()
) {
  def path(): String = this.url.pathname + this.url.search + this.url.hash

  //
  // Node
  //

  def node(uuid: String): Option[Node] = this.nodes.get(uuid)

  def isSelectingNode(node: Node): Boolean =
    this.selectedNodeId.map(_ == node.uuid).getOrElse(false)

  def localNode(): Option[Node] = this.localNodeId.flatMap(node(_))

  def operatingNode(): Option[Node] = this.operatingNodeId.flatMap(node(_))

  def selectedNode(): Option[Node] = this.selectedNodeId.flatMap(node(_))

  def currentNode(): Option[Node] = selectedNode().orElse(this.operatingNode())

  //
  // Cotonoma
  //

  def cotonoma(uuid: String): Option[Cotonoma] = this.cotonomas.get(uuid)

  def rootCotonomaId(): Option[String] =
    currentNode().flatMap(node => Option(node.root_cotonoma_id))

  def isSelectingCotonoma(cotonoma: Cotonoma): Boolean =
    this.selectedCotonomaId.map(_ == cotonoma.uuid).getOrElse(false)

  def selectedCotonoma(): Option[Cotonoma] =
    this.selectedCotonomaId.flatMap(cotonoma(_))

  def superCotonomas(): Seq[Cotonoma] =
    this.superCotonomaIds.map(this.cotonoma(_)).flatten

  def subCotonomas(): Seq[Cotonoma] =
    this.subCotonomaIds.map(this.cotonoma(_)).flatten

  def currentCotonomaId(): Option[String] =
    this.selectedCotonomaId.orElse(currentNode().map(_.root_cotonoma_id))

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
