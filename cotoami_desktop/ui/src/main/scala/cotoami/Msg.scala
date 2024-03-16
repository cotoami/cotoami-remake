package cotoami

import cotoami.Log
import cotoami.backend.{LogEvent, Node, SystemInfo}

sealed trait Msg

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
case class DatabaseOpened(result: Either[backend.Error, Node]) extends Msg

// Pane
case class TogglePane(name: String) extends Msg
case class ResizePane(name: String, newSize: Int) extends Msg

// Sub
case class WelcomeModalMsg(subMsg: subparts.WelcomeModal.Msg) extends Msg

// Commands
case object FetchLocalNode extends Msg
case class LocalNodeFetched(result: Either[backend.Error, Node]) extends Msg
