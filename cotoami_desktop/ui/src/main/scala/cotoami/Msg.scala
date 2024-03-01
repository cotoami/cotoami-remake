package cotoami

import cotoami.backend.SystemInfo

sealed trait Msg

case class ErrorTest(result: Either[backend.Error, String]) extends Msg
case object SelectDirectory extends Msg
case class DirectorySelected(result: Either[Throwable, Option[String]])
    extends Msg

case object ToggleLogView extends Msg
case class SystemInfoFetched(result: Either[Unit, SystemInfo]) extends Msg
case class UiStateRestored(state: Option[Model.UiState]) extends Msg

case class TogglePane(name: String) extends Msg
case class ResizePane(name: String, newSize: Int) extends Msg
