package cotoami.backend

import scala.scalajs.js

import marubinotto.facade.Nullable
import cotoami.models.SystemMessages

@js.native
trait LogEventJson extends js.Object {
  val level: String = js.native
  val message: String = js.native
  val details: Nullable[String] = js.native
}

object LogEventJson {
  def toMessage(event: LogEventJson): SystemMessages.Entry =
    SystemMessages.Entry(
      SystemMessages.categories.get(event.level)
        .getOrElse(SystemMessages.Info),
      event.message,
      Nullable.toOption(event.details)
    )
}
