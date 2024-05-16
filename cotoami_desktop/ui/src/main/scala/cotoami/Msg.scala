package cotoami

import scala.util.chaining._
import org.scalajs.dom.URL

import cotoami.utils.Log
import cotoami.backend._
import cotoami.repositories._

sealed trait Msg

case class UrlChanged(url: URL) extends Msg

// Log
case class AddLogEntry(
    level: Log.Level,
    message: String,
    details: Option[String] = None
) extends Msg
case class LogEvent(event: LogEventJson) extends Msg
case class BackendEvent(event: BackendEventJson) extends Msg
case object ToggleLogView extends Msg

// App init
case class SystemInfoFetched(result: Either[Unit, SystemInfoJson]) extends Msg
case class UiStateRestored(state: Option[Model.UiState]) extends Msg
case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfoJson])
    extends Msg

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
case class DomainMsg(subMsg: Domain.Msg) extends Msg

// Subparts
case class FlowInputMsg(subMsg: subparts.FormCoto.Msg) extends Msg
case class SectionTimelineMsg(subMsg: subparts.SectionTimeline.Msg) extends Msg
case class SectionTraversalsMsg(subMsg: subparts.SectionTraversals.Msg)
    extends Msg
case class ModalWelcomeMsg(subMsg: subparts.ModalWelcome.Msg) extends Msg

object Msg {
  lazy val FetchMoreRecentCotonomas =
    Cotonomas.FetchMoreRecent.pipe(Domain.CotonomasMsg).pipe(DomainMsg)

  def FetchMoreSubCotonomas(id: Id[Cotonoma]) =
    Cotonomas.FetchMoreSubs(id).pipe(Domain.CotonomasMsg).pipe(DomainMsg)

  lazy val FetchMoreTimeline =
    Cotos.FetchMoreTimeline.pipe(Domain.CotosMsg).pipe(DomainMsg)

  def FetchGraphFromCoto(coto: Id[Coto]) =
    Domain.FetchGraphFromCoto(coto).pipe(DomainMsg)

  def OpenTraversal(start: Id[Coto]) =
    subparts.SectionTraversals.OpenTraversal(start).pipe(SectionTraversalsMsg)
}
