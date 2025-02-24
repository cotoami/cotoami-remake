package cotoami

import org.scalajs.dom.URL

import cotoami.utils.Log
import cotoami.backend._
import cotoami.repository._
import cotoami.models._
import cotoami.subparts._

sealed trait Msg extends Into[Msg] {
  def into = this
}

object Msg {
  case class UrlChanged(url: URL) extends Msg

  // Log
  case class AddLogEntry(
      level: Log.Level,
      message: String,
      details: Option[String] = None
  ) extends Msg
  case class LogEvent(event: LogEventJson) extends Msg
  case class BackendChange(log: ChangelogEntryJson) extends Msg
  case class BackendEvent(event: LocalNodeEventJson) extends Msg
  case object ToggleLogView extends Msg

  // App init
  case class SystemInfoFetched(result: Either[Unit, SystemInfoJson]) extends Msg
  case class UiStateRestored(state: Option[UiState]) extends Msg
  case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfo]) extends Msg
  case class SetDatabaseInfo(info: DatabaseInfo) extends Msg
  case class ServerConnectionsInitialized(result: Either[ErrorJson, Null])
      extends Msg
  case class SetRemoteInitialDataset(dataset: InitialDataset) extends Msg

  // UI
  case class SetTheme(theme: String) extends Msg
  case class OpenOrClosePane(name: String, open: Boolean) extends Msg
  case class ResizePane(name: String, newSize: Int) extends Msg

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
  case object SelectionCleared extends Msg

  // Highlight
  case class Highlight(id: Id[Coto]) extends Msg
  case object Unhighlight extends Msg

  // Repository
  case object ReloadRepository extends Msg
  case class RepositoryMsg(submsg: Root.Msg) extends Msg

  // Map
  case object OpenGeomap extends Msg
  case object CloseMap extends Msg
  case class FocusGeolocation(location: Geolocation) extends Msg
  case object UnfocusGeolocation extends Msg
  case object DisplayGeolocationInFocus extends Msg

  // Subparts
  case class ModalMsg(submsg: Modal.Msg) extends Msg
  case class NavCotonomasMsg(submsg: NavCotonomas.Msg) extends Msg
  case class PaneSearchMsg(submsg: PaneSearch.Msg) extends Msg
  case class FlowInputMsg(submsg: SectionFlowInput.Msg) extends Msg
  case class SectionTimelineMsg(submsg: SectionTimeline.Msg) extends Msg
  case class SectionPinsMsg(submsg: SectionPins.Msg) extends Msg
  case class SectionTraversalsMsg(submsg: SectionTraversals.Msg) extends Msg
  case class SectionGeomapMsg(submsg: SectionGeomap.Msg) extends Msg
}
