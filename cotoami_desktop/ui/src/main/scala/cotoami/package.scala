import scala.scalajs.js
import cats.effect.IO
import fui.FunctionalUI._

package object cotoami {

  case class Id[T](uuid: String) extends AnyVal

  def log_debug(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Debug, message, details)) })
  def log_info(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Info, message, details)) })
  def log_warn(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Warn, message, details)) })
  def log_error(message: String, details: Option[String] = None): Cmd[Msg] =
    Cmd(IO { Some(AddLogEntry(Log.Error, message, details)) })

  def node_command[T](command: js.Object): Cmd[Either[backend.Error, T]] =
    tauri.invokeCommand(
      "node_command",
      js.Dynamic.literal(command = command)
    ).map((e: Either[backend.Error, String]) =>
      e.map(js.JSON.parse(_).asInstanceOf[T])
    )

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
