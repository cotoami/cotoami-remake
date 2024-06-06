package cotoami.backend

import scala.scalajs.js
import cotoami.utils.Log

@js.native
trait LogEventJson extends js.Object {
  val level: String = js.native
  val message: String = js.native
  val details: Nullable[String] = js.native
}

object LogEventJson {
  def toLogEntry(event: LogEventJson): Log.Entry =
    Log.Entry(
      Log.levels.get(event.level).getOrElse(Log.Debug),
      event.message,
      Nullable.toOption(event.details)
    )
}
