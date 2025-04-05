import scala.scalajs.js

import cats.effect.IO

import marubinotto.fui._
import cotoami.utils.Log
import cotoami.backend.ErrorJson

package object cotoami {

  trait Into[T] {
    def into: T
  }

  def info(
      message: String,
      details: Option[String] = None
  ): Cmd.One[Msg] =
    Cmd(IO { Some(Msg.AddLogEntry(Log.Info, message, details)) })

  def error(
      message: String,
      details: Option[String] = None
  ): Cmd.One[Msg] =
    Cmd(IO { Some(Msg.AddLogEntry(Log.Error, message, details)) })

  def error(
      message: String,
      errorJson: ErrorJson
  ): Cmd.One[Msg] = error(message, Some(js.JSON.stringify(errorJson)))
}
