package cotoami.backend

import scala.scalajs.js
import fui.FunctionalUI.Cmd
import cotoami.tauri

@js.native
trait SystemInfo extends js.Object {
  val app_data_dir: String = js.native
}

object SystemInfo {
  def fetch[Msg](createMsg: Either[Throwable, SystemInfo] => Msg): Cmd[Msg] =
    tauri.invokeCommand(createMsg, "system_info")
}
