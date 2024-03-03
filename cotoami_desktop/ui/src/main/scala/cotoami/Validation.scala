package cotoami

import slinky.core.AttrPair
import slinky.core.facade.ReactElement
import slinky.web.html._

object Validation {
  case class Error(
      code: String,
      message: String,
      params: Map[String, String] = Map.empty
  )

  def nonBlank(value: String): Option[Error] =
    if (value.isBlank())
      Some(Error("blank", "This must not be blank."))
    else
      None

  def length(value: String, min: Int, max: Int): Option[Error] =
    if (value.length() < min)
      Some(
        Error(
          "length-min",
          s"This must be longer than ${min}.",
          Map("min" -> min.toString())
        )
      )
    else if (value.length() > max)
      Some(
        Error(
          "length-max",
          s"This must be shorter than ${max}.",
          Map("max" -> max.toString())
        )
      )
    else
      None

  def ariaInvalid(errors: Option[Seq[Error]]): AttrPair[input.tagType] = {
    (aria - "invalid") :=
      errors
        .map(e => if (e.isEmpty) "false" else "true")
        .getOrElse("")
  }

  def validationErrorDiv(errors: Option[Seq[Error]]): ReactElement =
    errors.flatMap(errors =>
      errors.headOption.map(e =>
        div(className := "validation-error")(
          e.message
        )
      )
    )
}
