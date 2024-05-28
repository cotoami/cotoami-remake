package cotoami.utils

import com.softwaremill.quicklens._

import slinky.core.AttrPair
import slinky.core.facade.ReactElement
import slinky.web.html._

object Validation {
  case class Error(
      code: String,
      message: String,
      params: Map[String, String] = Map.empty
  )

  case class Result(errors: Option[Seq[Validation.Error]] = None) {
    def validating: Boolean = this.errors.isEmpty

    def validated: Boolean =
      this.errors.map(_.isEmpty).getOrElse(false)

    def firstError: Option[Validation.Error] =
      this.errors.flatMap(_.headOption)

    def addError(error: Validation.Error): Result =
      this.addErrors(Seq(error))

    def addErrors(errors: Seq[Validation.Error]): Result =
      this.modify(_.errors).using(
        _ match {
          case Some(existing) => Some(existing ++ errors)
          case None           => Some(errors)
        }
      )
  }

  object Result {
    def apply(error: Validation.Error): Result =
      Result(Some(Seq(error)))

    def apply(errors: Seq[Validation.Error]): Result =
      Result(Some(errors))

    def validated(): Result = Result(Seq.empty)
  }

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

  def ariaInvalid(result: Validation.Result): AttrPair[input.tagType] = {
    (aria - "invalid") :=
      (if (result.validating)
         ""
       else if (result.validated)
         "false"
       else
         "true")
  }

  def sectionValidationError(result: Validation.Result): ReactElement =
    result.firstError.map(e =>
      section(className := "validation-error")(
        e.message
      )
    )
}
