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
case class BackendLogEvent(event: LogEventJson) extends Msg
case object ToggleLogView extends Msg

// App init
case class SystemInfoFetched(result: Either[Unit, SystemInfoJson]) extends Msg
case class UiStateRestored(state: Option[Model.UiState]) extends Msg
case class DatabaseOpened(result: Either[ErrorJson, DatabaseInfoJson])
    extends Msg

// UI
case class TogglePane(name: String) extends Msg
case class ResizePane(name: String, newSize: Int) extends Msg
case class ToggleContent(cotoViewId: String) extends Msg

// Transition
case class SelectNode(id: Id[Node]) extends Msg
case object DeselectNode extends Msg
case class SelectCotonoma(id: Id[Cotonoma]) extends Msg
case object DeselectCotonoma extends Msg

// Domain
case class DomainMsg(subMsg: Domain.Msg) extends Msg

// Subparts
case class FlowInputMsg(subMsg: subparts.FormCoto.Msg) extends Msg
case class ModalWelcomeMsg(subMsg: subparts.ModalWelcome.Msg) extends Msg

object Msg {
  lazy val FetchMoreRecentCotonomas =
    Cotonomas.FetchMoreRecent.pipe(Domain.CotonomasMsg).pipe(DomainMsg)

  def FetchMoreSubCotonomas(id: Id[Cotonoma]) =
    Cotonomas.FetchMoreSubs(id).pipe(Domain.CotonomasMsg).pipe(DomainMsg)

  lazy val FetchMoreTimeline =
    Cotos.FetchMoreTimeline.pipe(Domain.CotosMsg).pipe(DomainMsg)
}
