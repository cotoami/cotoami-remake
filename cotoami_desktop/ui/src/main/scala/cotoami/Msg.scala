package cotoami

import scala.scalajs.js
import org.scalajs.dom.URL

import cotoami.Log
import cotoami.backend.{
  CotonomaJson,
  DatabaseInfoJson,
  LogEvent,
  Node,
  NodeJson,
  Paginated,
  SystemInfo
}

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

// Sub
case class ModalWelcomeMsg(subMsg: subparts.ModalWelcome.Msg) extends Msg

// Commands
case object FetchMoreCotonomas extends Msg
case class CotonomasFetched(
    result: Either[backend.Error, Paginated[CotonomaJson]]
) extends Msg
