package cotoami

import org.scalajs.dom.URL

import cotoami.Log
import cotoami.backend.{Cotonoma, DatabaseInfoJson, LogEvent, Node, SystemInfo}

sealed trait Msg

case class UrlChanged(url: URL) extends Msg

// Log
case class AddLogEntry(
    level: Log.Level,
    message: String,
    details: Option[String] = None
) extends Msg
case class BackendLogEvent(event: LogEvent) extends Msg
case object ToggleLogView extends Msg

// App init
case class SystemInfoFetched(result: Either[Unit, SystemInfo]) extends Msg
case class UiStateRestored(state: Option[Model.UiState]) extends Msg
case class DatabaseOpened(result: Either[backend.Error, DatabaseInfoJson])
    extends Msg

// Pane
case class TogglePane(name: String) extends Msg
case class ResizePane(name: String, newSize: Int) extends Msg

// Transition
case class SelectNode(id: Id[Node]) extends Msg
case object DeselectNode extends Msg
case class SelectCotonoma(id: Id[Cotonoma]) extends Msg
case object DeselectCotonoma extends Msg

// Sub
case class CotonomasMsg(subMsg: backend.Cotonomas.Msg) extends Msg
case class FlowInputMsg(subMsg: subparts.FormCoto.Msg) extends Msg
case class ModalWelcomeMsg(subMsg: subparts.ModalWelcome.Msg) extends Msg
