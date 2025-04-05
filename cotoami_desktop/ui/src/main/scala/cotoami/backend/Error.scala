package cotoami.backend

import scala.scalajs.js

import marubinotto.Validation

@js.native
trait ErrorJson extends js.Object {
  val code: String = js.native
  val default_message: String = js.native
  val params: js.Dictionary[String] = js.native
}

object ErrorJson {
  def toValidationError(error: ErrorJson): Validation.Error =
    Validation.Error(error.code, error.default_message, error.params.toMap)
}
