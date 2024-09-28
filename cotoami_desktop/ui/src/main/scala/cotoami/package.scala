import cats.effect.IO

import fui._
import cotoami.utils.Log

package object cotoami {

  def log_debug(
      message: String,
      details: Option[String] = None
  ): Cmd.Single[Msg] =
    Cmd(IO { Some(Msg.AddLogEntry(Log.Debug, message, details)) })
  def log_info(
      message: String,
      details: Option[String] = None
  ): Cmd.Single[Msg] =
    Cmd(IO { Some(Msg.AddLogEntry(Log.Info, message, details)) })
  def log_warn(
      message: String,
      details: Option[String] = None
  ): Cmd.Single[Msg] =
    Cmd(IO { Some(Msg.AddLogEntry(Log.Warn, message, details)) })
  def log_error(
      message: String,
      details: Option[String] = None
  ): Cmd.Single[Msg] =
    Cmd(IO { Some(Msg.AddLogEntry(Log.Error, message, details)) })
}
