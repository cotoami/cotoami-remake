package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.utils.facade.Nullable
import cotoami.libs.tauri

@js.native
trait SystemInfoJson extends js.Object {
  val app_version: String = js.native
  val resource_dir: Nullable[String] = js.native
  val app_config_dir: Nullable[String] = js.native
  val app_data_dir: Nullable[String] = js.native
  val time_zone_offset_in_sec: Int = js.native
  val os: String = js.native
  val recent_databases: js.Array[DatabaseOpenedJson] = js.native
}

object SystemInfoJson {
  def fetch(): Cmd.One[Either[Unit, SystemInfoJson]] =
    tauri.invokeCommand("system_info")

  def debug(info: SystemInfoJson): String = {
    val recent_databases = info.recent_databases.map(x => (x.name, x.folder))
    val s = new StringBuilder
    s ++= s"app_version: ${info.app_version}"
    s ++= s", resource_dir: ${info.resource_dir}"
    s ++= s", app_config_dir: ${info.app_config_dir}"
    s ++= s", app_data_dir: ${info.app_data_dir}"
    s ++= s", time_zone_offset_in_sec: ${info.time_zone_offset_in_sec}"
    s ++= s", os: ${info.os}"
    s ++= s", recent_databases: ${recent_databases}"
    s.result()
  }
}

@js.native
trait DatabaseOpenedJson extends js.Object {
  val folder: String = js.native
  val name: String = js.native
  val icon: String = js.native
}
