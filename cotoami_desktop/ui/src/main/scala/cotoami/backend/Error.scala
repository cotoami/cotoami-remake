package cotoami.backend

import scala.scalajs.js

import fui.FunctionalUI.Cmd
import cotoami.utils.Validation

@js.native
trait ErrorJson extends js.Object {
  val code: String = js.native
  val message: String = js.native
  val details: String = js.native
}

object ErrorJson {
  def toValidationError(error: ErrorJson): Validation.Error =
    Validation.Error(error.code, error.message)

  def log(error: ErrorJson, message: String): Cmd[cotoami.Msg] =
    cotoami.log_error(message, Some(js.JSON.stringify(error)))
}
