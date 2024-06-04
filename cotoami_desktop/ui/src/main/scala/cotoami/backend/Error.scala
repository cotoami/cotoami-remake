package cotoami.backend

import scala.scalajs.js

import fui.Cmd
import cotoami.utils.Validation

@js.native
trait ErrorJson extends js.Object {
  val code: String = js.native
  val default_message: String = js.native
  val params: js.Dictionary[String] = js.native
}

object ErrorJson {
  def toValidationError(error: ErrorJson): Validation.Error =
    Validation.Error(error.code, error.default_message, error.params.toMap)

  def log(error: ErrorJson, message: String): Cmd[cotoami.Msg] =
    cotoami.log_error(message, Some(js.JSON.stringify(error)))
}
