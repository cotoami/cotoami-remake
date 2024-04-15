import scala.scalajs.js
import cats.effect.IO

import fui.FunctionalUI._
import cotoami.utils.Log

package object cotoami {

  def log_debug(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Debug, message, details)) })
  def log_info(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Info, message, details)) })
  def log_warn(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Warn, message, details)) })
  def log_error(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Error, message, details)) })

  def openDatabase(folder: String): Cmd[Msg] =
    tauri
      .invokeCommand(
        "open_database",
        js.Dynamic
          .literal(
            databaseFolder = folder
          )
      )
      .map(DatabaseOpened)
}
