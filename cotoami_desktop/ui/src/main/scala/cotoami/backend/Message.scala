package cotoami.backend

import scala.scalajs.js

import marubinotto.facade.Nullable
import cotoami.models.SystemMessages

@js.native
trait MessageJson extends js.Object {
  val category: String = js.native
  val message: String = js.native
  val details: Nullable[String] = js.native
}

object MessageJson {
  def toMessage(json: MessageJson): SystemMessages.Entry =
    SystemMessages.Entry(
      SystemMessages.categories.get(json.category)
        .getOrElse(SystemMessages.Info),
      json.message,
      Nullable.toOption(json.details)
    )
}
