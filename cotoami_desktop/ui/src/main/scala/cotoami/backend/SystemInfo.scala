package cotoami.backend

import scala.scalajs.js
import fui.FunctionalUI.Cmd
import cotoami.tauri

@js.native
trait SystemInfo extends js.Object {
  val app_version: String = js.native
  val app_config_dir: String = js.native
  val app_data_dir: String = js.native
}

object SystemInfo {
  def fetch(): Cmd[Either[Unit, SystemInfo]] =
    tauri.invokeCommand("system_info")
}
