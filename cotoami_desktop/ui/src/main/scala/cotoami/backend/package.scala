package cotoami

import scala.scalajs.js
import slinky.web.html.max

import cotoami.{Log, Validation}

package object backend {

  @js.native
  trait Error extends js.Object {
    val code: String = js.native
    val message: String = js.native
    val details: String = js.native
  }

  object Error {
    def toValidationError(error: Error): Validation.Error =
      Validation.Error(error.code, error.message)
  }

  @js.native
  trait LogEvent extends js.Object {
    val level: String = js.native
    val message: String = js.native
    val details: String = js.native
  }

  object LogEvent {
    def toLogEntry(event: LogEvent): Log.Entry =
      Log.Entry(
        Log.levels.get(event.level).getOrElse(Log.Debug),
        event.message,
        Option(event.details)
      )
  }
}
