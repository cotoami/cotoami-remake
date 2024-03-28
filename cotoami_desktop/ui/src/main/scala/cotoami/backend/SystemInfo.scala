package cotoami.backend

import scala.scalajs.js
import fui.FunctionalUI.Cmd
import cotoami.tauri

@js.native
trait SystemInfo extends js.Object {
  val app_version: String = js.native
  val app_config_dir: String = js.native
  val app_data_dir: String = js.native
  val recent_databases: js.Array[DatabaseOpened] = js.native
}

object SystemInfo {
  def fetch(): Cmd[Either[Unit, SystemInfo]] =
    tauri.invokeCommand("system_info")

  def debug(info: SystemInfo): String = {
    val recent_databases = info.recent_databases.map(x => (x.name, x.folder))
    val s = new StringBuilder
    s ++= s"app_version: ${info.app_version}"
    s ++= s", app_config_dir: ${info.app_config_dir}"
    s ++= s", app_data_dir: ${info.app_data_dir}"
    s ++= s", recent_databases: ${recent_databases}"
    s.result()
  }
}

@js.native
trait DatabaseOpened extends js.Object {
  val folder: String = js.native
  val name: String = js.native
  val icon: String = js.native
}
