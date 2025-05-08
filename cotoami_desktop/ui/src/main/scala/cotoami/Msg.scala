package cotoami

import org.scalajs.dom.URL

import cotoami.backend._
import cotoami.repository._
import cotoami.models._
import cotoami.subparts._

sealed trait Msg extends Into[Msg] {
  def into = this
}

object Msg {
  case class UrlChanged(url: URL) extends Msg

  // System messages
  case class AddMessage(
      category: SystemMessages.Category,
      message: String,
      details: Option[String] = None
  ) extends Msg

  // Backend events
  case class BackendMessage(message: MessageJson) extends Msg
  case class BackendChange(log: ChangelogEntryJson) extends Msg
  case class BackendEvent(event: LocalNodeEventJson) extends Msg

  // App init
  case class SystemInfoFetched(result: Either[Unit, SystemInfoJson]) extends Msg
  case class UiStateRestored(uiState: Option[UiState]) extends Msg
  case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfo]) extends Msg
  case class SetDatabaseInfo(info: DatabaseInfo) extends Msg
  case class ServerConnectionsInitialized(result: Either[ErrorJson, Null])
      extends Msg
  case class SetInitialDataset(dataset: InitialDataset) extends Msg

  // UI
  case class SetTheme(theme: String) extends Msg
  case class SetPaneOpen(name: String, open: Boolean) extends Msg
  case class ResizePane(name: String, newSize: Int) extends Msg
  case object SwapPane extends Msg

  // Focus
  case class FocusNode(id: Id[Node]) extends Msg
  case object UnfocusNode extends Msg
  case class FocusCotonoma(cotonoma: Cotonoma) extends Msg
  case class FocusedCotonomaDetailsFetched(
      result: Either[ErrorJson, CotonomaDetails]
  ) extends Msg
  case object UnfocusCotonoma extends Msg
  case class FocusCoto(id: Id[Coto], moveTo: Boolean = true) extends Msg
  case class FocusedCotoDetailsFetched(
      result: Either[ErrorJson, CotoDetails]
  ) extends Msg
  case object UnfocusCoto extends Msg

  // Select
  case class Select(id: Id[Coto]) extends Msg
  case class Deselect(id: Id[Coto]) extends Msg

  // Highlight
  case class Highlight(id: Id[Coto]) extends Msg
  case object Unhighlight extends Msg

  // Repository
  case object ReloadRepository extends Msg
  case class RepositoryMsg(submsg: Root.Msg) extends Msg
  case class Pin(cotoId: Id[Coto]) extends Msg
  case class NodeUpdated(result: Either[ErrorJson, NodeDetails]) extends Msg
  case class CotoUpdated(result: Either[ErrorJson, CotoDetails]) extends Msg
  case class Promoted(result: Either[ErrorJson, (Cotonoma, Coto)]) extends Msg

  // Subparts
  case class ModalMsg(submsg: Modal.Msg) extends Msg
  case class ViewMessagesMsg(submsg: ViewMessages.Msg) extends Msg
  case class NavCotonomasMsg(submsg: NavCotonomas.Msg) extends Msg
  case class SectionNodeToolsMsg(submsg: SectionNodeTools.Msg) extends Msg
  case class AppMainMsg(submsg: AppMain.Msg) extends Msg
  case class PaneStockMsg(submsg: PaneStock.Msg) extends Msg
  case class PaneSearchMsg(submsg: PaneSearch.Msg) extends Msg
  case class FlowInputMsg(submsg: SectionFlowInput.Msg) extends Msg
  case class SectionTimelineMsg(submsg: SectionTimeline.Msg) extends Msg
  case class SectionPinsMsg(submsg: SectionPins.Msg) extends Msg
  case class SectionTraversalsMsg(submsg: SectionTraversals.Msg) extends Msg
  case class SectionGeomapMsg(submsg: SectionGeomap.Msg) extends Msg
}
