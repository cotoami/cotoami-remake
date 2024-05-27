package cotoami.utils

import slinky.core.AttrPair
import slinky.core.facade.ReactElement
import slinky.web.html._

object Validation {
  case class Error(
      code: String,
      message: String,
      params: Map[String, String] = Map.empty
  )

  def nonBlank(name: String, value: String): Option[Error] =
    if (value.isBlank())
      Some(
        Error(
          "blank",
          s"${name.capitalize} must not be blank.",
          Map("name" -> name)
        )
      )
    else
      None

  def length(name: String, value: String, min: Int, max: Int): Option[Error] =
    if (value.length() < min)
      Some(
        Error(
          "length-min",
          s"${name.capitalize} must be longer than ${min}.",
          Map("name" -> name, "min" -> min.toString())
        )
      )
    else if (value.length() > max)
      Some(
        Error(
          "length-max",
          s"${name.capitalize} must be shorter than ${max}.",
          Map("name" -> name, "max" -> max.toString())
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

  def sectionValidationError(errors: Option[Seq[Error]]): ReactElement =
    errors.flatMap(errors =>
      errors.headOption.map(e =>
        section(className := "validation-error")(
          e.message
        )
      )
    )
}
