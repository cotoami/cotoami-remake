package cotoami

import org.scalajs.dom.URL

import cotoami.utils.Log
import cotoami.backend._
import cotoami.repositories._
import cotoami.models._
import cotoami.subparts._

sealed trait Msg

case class UrlChanged(url: URL) extends Msg

// Log
case class AddLogEntry(
    level: Log.Level,
    message: String,
    details: Option[String] = None
) extends Msg
case class LogEvent(event: LogEventJson) extends Msg
case class BackendChange(log: ChangelogEntryJson) extends Msg
case object ToggleLogView extends Msg

// App init
case class SystemInfoFetched(result: Either[Unit, SystemInfoJson]) extends Msg
case class UiStateRestored(state: Option[UiState]) extends Msg
case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfoJson])
    extends Msg
case class SetDatabaseInfo(info: DatabaseInfo) extends Msg

// UI
case class OpenOrClosePane(name: String, open: Boolean) extends Msg
case class ResizePane(name: String, newSize: Int) extends Msg
case class SwitchPinnedView(cotonoma: Id[Cotonoma], inColumns: Boolean)
    extends Msg
case class ScrollToPinnedCoto(pin: Link) extends Msg

// Transition
case class SelectNode(id: Id[Node]) extends Msg
case object DeselectNode extends Msg
case class SelectCotonoma(id: Id[Cotonoma]) extends Msg
case object DeselectCotonoma extends Msg

// Domain
case object ReloadDomain extends Msg
case class DomainMsg(subMsg: Domain.Msg) extends Msg

// Subparts
case class FlowInputMsg(subMsg: FormCoto.Msg) extends Msg
case class SectionTimelineMsg(subMsg: SectionTimeline.Msg) extends Msg
case class SectionTraversalsMsg(subMsg: SectionTraversals.Msg) extends Msg
case class OpenModal(modal: Modal.Model) extends Msg
case object CloseModal extends Msg
case class ModalMsg(subMsg: Modal.Msg) extends Msg
